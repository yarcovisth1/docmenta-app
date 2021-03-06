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

import java.util.Locale;
import java.util.Map;
import org.docma.app.RuleConfig;
import org.docma.coreapi.implementation.DefaultLog;
import org.docma.plugin.LogLevel;
import org.docma.plugin.StoreConnection;
import org.docma.plugin.UserSession;
import org.docma.plugin.rules.HTMLRuleContext;

/**
 *
 * @author MP
 */
public class HTMLRuleContextImpl implements HTMLRuleContext
{
    private static final Locale DEFAULT_LOCALE = new Locale("en");
    
    private final StoreConnectionImpl conn;
    private final DefaultLog log;
    private StringBuilder content = null;
    private String nodeId = null;
    private RuleConfig ruleConfig = null;
    private Map<Object, Object> props = null;
    private boolean isModeSave = false;
    private boolean allowAutoCorrect = true;
    
    /* ------- Constructor --------- */
    
    public HTMLRuleContextImpl(StoreConnectionImpl conn)
    {
        this.conn = conn;
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
        return allowAutoCorrect && 
               ((isModeCheck() && ruleConfig.isCorrectOnCheck(checkId)) || 
                (isModeSave()  && ruleConfig.isCorrectOnSave(checkId)));
    }
    
    public void log(String checkId, String msg, Object... args)
    {
        if (isEnabled(checkId)) {
            log.add(ruleConfig.getLogLevel(checkId), getCheckName(checkId), msg, args);
        }
    }
    
    public void log(String checkId, int contentPosition, String msg, Object... args)
    {
        if (isEnabled(checkId)) {
            LogLevel level = ruleConfig.getLogLevel(checkId);
            logPos(level, checkId, contentPosition, msg, args);
        }
    }

    public void logInfo(String checkId, String msg, Object... args) 
    {
        if (isEnabled(checkId)) {
            log.add(LogLevel.INFO, getCheckName(checkId), msg, args);
        }
    }

    public void logInfo(String checkId, int contentPosition, String msg, Object... args) 
    {
        if (isEnabled(checkId)) {
            logPos(LogLevel.INFO, checkId, contentPosition, msg, args);
        }
    }
    
    public void logHeader(int level, String headline, Object... args)
    {
        log.addHeader(level, headline, args);
    }
    
    public void logText(String headline, String txt)
    {
        log.addPreformatted(headline, txt);
    }
    
    public void log(LogLevel level, String msg, Object... args) 
    {
        log.add(level, getCheckName(null), msg, args);
    }

    public void log(LogLevel level, int contentPosition, String msg, Object... args) 
    {
        logPos(level, null, contentPosition, msg, args);
    }
    
    public String getNodeId() 
    {
        return nodeId;
    }
    
    public StoreConnection getStoreConnection()
    {
        return conn;
    }
    
    public Locale getUILocale()
    {
        UserSession sess = (conn == null) ? null : conn.getUserSession();
        Locale loc = (sess == null) ? null : sess.getCurrentLocale();
        return (loc == null) ? DEFAULT_LOCALE : loc;
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

    public void setContent(StringBuilder content)
    {
        this.content = content;
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
    
    public void clearLog()
    {
        this.log.clear();
    }

    /* ------- Private methods --------- */

    private void logPos(LogLevel level, String checkId, int contentPosition, String msg, Object... args)
    {
        String generator = getCheckName(checkId);
        String content_extract = "...." + extract(content, contentPosition) + "....";
        log.add(level, generator, content_extract, msg, args);
    }

    private String extract(CharSequence content, int contentPos)
    {
        if ((content == null) || (contentPos < 0)) {
            return null;
        }
        
        final int CTX_SIZE = 10;
        final int len = content.length();
        int startPos = (contentPos > CTX_SIZE) ? contentPos - CTX_SIZE : 0;
        int endPos = Math.min(len, contentPos + CTX_SIZE);
        
        // Search previous start tag within 40 characters
        int cnt = 0;
        char prev = content.charAt(startPos);
        while ((startPos > 0) && (cnt < 40)) {
            cnt++;
            startPos--;
            char ch = content.charAt(startPos);
            if ((ch == '<') && prev != '/') {
                break;
            }
        }
        
        // Search next end of tag within 40 characters
        cnt = 0;
        while ((endPos < len) && (cnt < 40)) {
            cnt++;
            char ch = content.charAt(endPos++);
            if (ch == '>') {
                break;
            }
        }
        
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
