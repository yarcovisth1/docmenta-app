/*
 * HTMLRuleContextImpl.java
 *
 *  Copyright (C) 2016  Manfred Paula, http://www.docmenta.org
 *   
 *  This file is part of Docmenta. Docmenta is free software: you can 
 *  redistribute it and/or modify it under the terms of the GNU Lesser 
 *  General Public License as published by the Free Software Foundation, 
 *  either version 3 of the License, or (at your option) any later version.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Docmenta.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.docma.plugin.implementation;

import java.util.Map;
import org.docma.app.RuleConfig;
import org.docma.coreapi.implementation.DefaultLog;
import org.docma.plugin.LogLevel;
import org.docma.plugin.StoreConnection;
import org.docma.plugin.rules.HTMLRuleContext;

/**
 *
 * @author MP
 */
public class HTMLRuleContextImpl implements HTMLRuleContext
{
    private final StoreConnectionImpl conn;
    private final StringBuilder content;
    private final DefaultLog log;
    private String nodeId = null;
    private RuleConfig ruleConfig = null;
    private Map<Object, Object> props = null;
    private boolean isModeSave = false;
    private boolean allowAutoCorrect = true;
    
    /* ------- Constructor --------- */
    
    public HTMLRuleContextImpl(StoreConnectionImpl conn, StringBuilder content)
    {
        this.conn = conn;
        this.content = content;
        this.log = new DefaultLog(conn.getI18n());
    }
    
    /* ------- Interface methods --------- */

    public boolean isEnabled(String checkId)
    {
        // If check is enabled/disabled by properties
        if ((props != null) && !props.isEmpty()) {
            Object obj = props.get(getCheckName(checkId));
            if (obj != null) {
                String val = obj.toString();
                if (val.equalsIgnoreCase("true") || val.equalsIgnoreCase("on")) {
                    return true;
                } else if (val.equalsIgnoreCase("false") || val.equalsIgnoreCase("off")) {
                    return false;
                }
            }
        }
        
        // Otherwise return configuration setting
        return (isModeCheck() && ruleConfig.isExecuteOnCheck(checkId)) ||
               (isModeSave() && ruleConfig.isExecuteOnSave(checkId));
    }
    
    public boolean isAutoCorrect(String checkId)
    {
        return allowAutoCorrect && ruleConfig.isCorrectOnSave(checkId);
    }
    
    public void log(String checkId, String msg, Object... args)
    {
        log.add(ruleConfig.getLogLevel(checkId), getCheckName(checkId), msg, args);
    }
    
    public void log(String checkId, int contentPosition, String msg, Object... args)
    {
        LogLevel level = ruleConfig.getLogLevel(checkId);
        String generator = getCheckName(checkId);
        final int CTX_SIZE = 16;
        int start_pos = (contentPosition > CTX_SIZE) ? contentPosition - CTX_SIZE : 0;
        // int rel_pos = contentPosition - start_pos;
        String content_extract = extract(content, start_pos, 2 * CTX_SIZE);
        
        log.add(level, generator, content_extract, msg, args);
    }
    
    public String getNodeId() 
    {
        return nodeId;
    }
    
    public StoreConnection getStoreConnection()
    {
        return conn;
    }
    
    /* ------- Other public methods --------- */

    public void setProperties(Map<Object, Object> props) 
    {
        this.props = props;
    }
    
    public void setAllowAutoCorrect(boolean enabled)
    {
        this.allowAutoCorrect = enabled;
    }
    
    public boolean isModeSave()
    {
        return isModeSave;
    }
    
    public boolean isModeCheck()
    {
        return !isModeSave;
    }
    
    public void setModeSave()
    {
        this.isModeSave = true;
    }
    
    public void setModeCheck()
    {
        this.isModeSave = false;
    }
    
    public void setNodeId(String nId) 
    {
        this.nodeId = nId;
    }
    
    public void setActiveRule(RuleConfig rc)
    {
        this.ruleConfig = rc;
    }
    
    public DefaultLog getLog()
    {
        return this.log;
    }
    
    /* ------- Private methods --------- */

    private String extract(CharSequence content, int startPos, int count)
    {
        int endPos = Math.min(content.length(), startPos + count);
        CharSequence sub = content.subSequence(startPos, endPos);
        
        // Transform all whitespace to space characters
        StringBuilder sb = new StringBuilder(sub);
        for (int i=0; i < sb.length(); i++) {
            char ch = sb.charAt(i);
            if (Character.isWhitespace(ch) && (ch != ' ')) {
                sb.setCharAt(i, ' ');
            }
        }
        return sb.toString();
    }
    
    private String getCheckName(String checkId)
    {
        if (checkId == null) {
            return (ruleConfig == null) ? "" : ruleConfig.getId();
        } else {
            if (ruleConfig == null) {
                return checkId;
            } else {
                return ruleConfig.getQualifiedCheckId(checkId);
            }
        }
    }
    
}
