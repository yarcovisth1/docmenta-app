/*
 * EditContentTransformer.java
 * 
 *  Copyright (C) 2013  Manfred Paula, http://www.docmenta.org
 *   
 *  This file is part of Docmenta. Docmenta is free software: you can 
 *  redistribute it and/or modify it under the terms of the GNU Lesser 
 *  General Public License as published by the Free Software Foundation, 
 *  either version 3 of the License, or (at your option) any later version.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Docmenta.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.docma.app;

import java.util.*;
import org.docma.coreapi.DocI18n;

import org.docma.coreapi.ProgressCallback;
import org.docma.plugin.DocmaException;
import org.docma.plugin.LogEntries;
import org.docma.plugin.LogEntry;
import org.docma.plugin.LogLevel;
import org.docma.plugin.implementation.HTMLRuleContextImpl;
import org.docma.plugin.implementation.LogEntriesImpl;
import org.docma.plugin.implementation.StoreConnectionImpl;
import org.docma.plugin.rules.HTMLRule;
import org.docma.util.Log;


/**
 *
 * @author MP
 */
public class EditContentTransformer
{
    // private static final String PADDING_PATTERN = "padding-left:";

    // public static final String PROP_TRANSFORM_TRIM_EMPTY_PARAS = "trim_empty_paras";
    // public static final String PROP_TRANSFORM_TRIM_FIGURE_SPACES = "trim_figure_spaces";

    public static LogEntries prepareHTMLForSave(StringBuilder content,
                                                String nodeId,
                                                Map<Object, Object> props,
                                                boolean allowAutoCorrect,
                                                DocmaSession docmaSess)
    {
        HTMLRuleContextImpl ctx = createHTMLRuleContext(docmaSess, props, true, true);
        List<RuleConfig> confs = getEnabledRuleConfigs(docmaSess, props);
        Map<String, HTMLRule> ruleObjs = null;
        try {
            ruleObjs = acquireRuleInstances(confs);
            callStartBatch(ctx, confs, ruleObjs);
            callApply(content, nodeId, allowAutoCorrect, ctx, confs, ruleObjs);
            callFinishBatch(ctx, confs, ruleObjs);
        } finally {
            releaseRuleInstances(confs, ruleObjs);
        }
        return new LogEntriesImpl(ctx.getLog());
    }

    public static LogEntries checkHTML(StringBuilder content,
                                       String nodeId,
                                       Map<Object, Object> props,
                                       boolean allowAutoCorrect,
                                       DocmaSession docmaSess)
    {
        HTMLRuleContextImpl ctx = createHTMLRuleContext(docmaSess, props, allowAutoCorrect, false);
        List<RuleConfig> confs = getEnabledRuleConfigs(docmaSess, props);
        Map<String, HTMLRule> ruleObjs = null;
        try {
            ruleObjs = acquireRuleInstances(confs);
            callStartBatch(ctx, confs, ruleObjs);
            callApply(content, nodeId, allowAutoCorrect, ctx, confs, ruleObjs);
            callFinishBatch(ctx, confs, ruleObjs);
        } finally {
            releaseRuleInstances(confs, ruleObjs);
        }
        return new LogEntriesImpl(ctx.getLog());
    }
    
    public static void checkNodesRecursive(String[] nodeIds,
                                           Map<Object, Object> props,
                                           boolean allowAutoCorrect,
                                           DocmaSession docmaSess, 
                                           ProgressCallback progress)
    {
        List<DocmaNode> nodes = new ArrayList<DocmaNode>(nodeIds.length);
        for (String nId : nodeIds) {
            DocmaNode nd = docmaSess.getNodeById(nId);
            if (nd != null) {
                nodes.add(nd);
            } else {
                progress.logWarning("consistency_check.node_id_not_found", nId);
            }
        }
        
        // Number of skipped/processed/updated nodes
        NodeCounts nodeCounts = new NodeCounts();
        
        // Number of warnings/errors per generator
        SortedMap<String, LogCounts> logStats = new TreeMap<String, LogCounts>();

        HTMLRuleContextImpl ctx = createHTMLRuleContext(docmaSess, props, allowAutoCorrect, false);
        List<RuleConfig> ruleConfs = getEnabledRuleConfigs(docmaSess, props);
        Map<String, HTMLRule> ruleObjs = null;
        try {
            ruleObjs = acquireRuleInstances(ruleConfs);
            callStartBatch(ctx, ruleConfs, ruleObjs);
            transferLogEntries(ctx, progress, logStats);

            StringBuilder contentBuffer = new StringBuilder(); // temporary working buffer  
            checkRecursive(nodes.toArray(new DocmaNode[nodes.size()]), 
                           1,        // depth
                           null,     // parent path (null for root nodes)
                           props, 
                           allowAutoCorrect, 
                           docmaSess, 
                           progress, 
                           logStats,
                           nodeCounts,
                           contentBuffer, 
                           ctx, ruleConfs, ruleObjs);
            
            callFinishBatch(ctx, ruleConfs, ruleObjs);
            transferLogEntries(ctx, progress, logStats);
        } catch (CanceledByUserException cbu) { // Canceled by user; do nothing
        } catch (Exception ex) {  // Runtime exceptions
            progress.logError("consistency_check.exception", ex.getMessage());
        } finally {
            releaseRuleInstances(ruleConfs, ruleObjs);
        }
        
        // Write Statistics
        progress.logHeader(1, "log_statistics.summary_header");
        DocI18n i18n = docmaSess.getI18n();

        String processedMsg = i18n.getLabel("consistency_check.processed_count", 
                                            nodeCounts.getProcessedCount(), 
                                            nodeCounts.getUpdatedCount());
        progress.logText(processedMsg, null);
        int skipped = nodeCounts.getSkippedCount();
        if (skipped > 0) {
            progress.logText(i18n.getLabel("consistency_check.skipped_non_html_count", skipped), null);
        }
        
        // Write Statistics per generator
        writeSummaryPerGenerator(logStats, LogLevel.WARNING, docmaSess, progress);
        writeSummaryPerGenerator(logStats, LogLevel.ERROR, docmaSess, progress);
        
        Integer err_cnt = progress.getErrorCount();
        Integer warn_cnt = progress.getWarningCount();
        String finished = i18n.getLabel("consistency_check.finished_summary", err_cnt, warn_cnt);
        progress.logText(finished, null);
        // progress.logHeader(2, "consistency_check.finished_summary", err_cnt, warn_cnt);
    }

    /* -------------  Private Methods ------------------ */
    
    private static void checkRecursive(DocmaNode[] nodes, 
                                       int depth, 
                                       String parentPath, 
                                       Map<Object, Object> props, 
                                       boolean allowAutoCorrect,
                                       DocmaSession docmaSess,                               
                                       ProgressCallback progress, 
                                       SortedMap<String, LogCounts> logStats,
                                       NodeCounts nodeCnts,
                                       StringBuilder contentBuffer,  // temporary working buffer
                                       HTMLRuleContextImpl ctx,
                                       List<RuleConfig> ruleConfs,
                                       Map<String, HTMLRule> ruleObjs)
    throws CanceledByUserException
    {
        progress.startWork(nodes.length);
        try {
            DocI18n i18n = docmaSess.getI18n();
            for (DocmaNode nd : nodes) {
                if (progress.getCancelFlag()) {
                    String cancelMsg = i18n.getLabel("consistency_check.canceled_by_user");
                    progress.logText(cancelMsg, null);
                    throw new CanceledByUserException("Consistency check canceled by user.");
                }
                try {
                    String nodePath = getNodePath(parentPath, nd);
                    if (nd.isSection()) {
                        DocmaNode[] children = nd.getChildren();
                        if ((children != null) && (children.length > 0)) {
                            checkRecursive(children, 
                                           depth + 1, 
                                           nodePath, 
                                           props, 
                                           allowAutoCorrect, 
                                           docmaSess, 
                                           progress, 
                                           logStats,
                                           nodeCnts,
                                           contentBuffer, 
                                           ctx, ruleConfs, ruleObjs);
                        }
                    } else if (nd.isHTMLContent()) {
                        String pathMsg = i18n.getLabel("consistency_check.entering_html_node", nodePath);
                        progress.logText(pathMsg, null);
                        String cont = nd.getContentString();
                        contentBuffer.setLength(0);   // clear buffer
                        contentBuffer.append(cont);
                        callApply(contentBuffer, nd.getId(), allowAutoCorrect, ctx, ruleConfs, ruleObjs);
                        
                        transferLogEntries(ctx, progress, logStats); // Transfer log entries to global log
                        nodeCnts.increaseProcessed();

                        if (allowAutoCorrect) {
                            String updated = contentBuffer.toString();
                            if (! cont.equals(updated)) {
                                nd.makeRevision();
                                nd.setContentString(updated);
                                progress.logInfo("consistency_check.node_updated", nodePath);
                                nodeCnts.increaseUpdated();
                            }
                        }
                    } else {
                        nodeCnts.increaseSkipped();
                    }
                } catch (Exception ex) {
                    progress.logError("consistency_check.exception", ex.getMessage());
                } finally {
                    progress.stepFinished();
                }
            }
        } finally {
            progress.finishWork();
        }
    }
    
    private static void transferLogEntries(HTMLRuleContextImpl ctx, 
                                           ProgressCallback progress, 
                                           SortedMap<String, LogCounts> logStats)
    {
        for (LogEntry entry : ctx.getLog().getLog()) {
            progress.log(entry);
            updateStats(entry, logStats);
        }
        ctx.clearLog();  // clear log because ctx is reused for other nodes
    }
    
    private static void updateStats(LogEntry entry, SortedMap<String, LogCounts> logStats)
    {
        String gen = entry.getGenerator();
        if (gen == null) {
            gen = "";
        }
        LogLevel lev = entry.getLevel();
        LogCounts counts = logStats.get(gen);
        if (counts == null) {
            counts = new LogCounts(gen);
            logStats.put(gen, counts);
        }
        if (lev == LogLevel.INFO) {
            counts.increaseInfos();
        } else if (lev == LogLevel.WARNING) {
            counts.increaseWarnings();
        } else if (lev == LogLevel.ERROR) {
            counts.increaseErrors();
        }
    }

    private static void writeSummaryPerGenerator(SortedMap<String, LogCounts> logStats, 
                                                 LogLevel level,
                                                 DocmaSession docmaSess, 
                                                 ProgressCallback progress)
    {
        final String dots  = "........................................";
        final String space = "                                        ";
        
        DocI18n i18n = docmaSess.getI18n();
        // String countLabel = i18n.getLabel("log_statistics.count");
        String totalLabel = i18n.getLabel("log_statistics.total");
        
        String headkey;
        switch (level) {
            case ERROR:   headkey = "log_statistics.errors_per_generator.header"; break;
            case WARNING: headkey = "log_statistics.warnings_per_generator.header"; break;
            case INFO:    headkey = "log_statistics.infos_per_generator.header"; break;
            default:      headkey = null;
        }
        if (headkey == null) {
            return;
        }
        
        StringBuilder stats = new StringBuilder();
        StringBuilder other = new StringBuilder();
        int totalCnt = 0;
        for (LogCounts cnts : logStats.values()) {
            String gen = cnts.getGenerator();
            int cnt;
            switch (level) {
                case ERROR:   cnt = cnts.getErrorCount(); break;
                case WARNING: cnt = cnts.getWarningCount(); break;
                case INFO:    cnt = cnts.getInfoCount(); break;
                default:      cnt = 0;
            }
            if (cnt > 0) {
                totalCnt += cnt;
                String cntStr = " " + cnt;
                boolean is_other = gen.equals("");
                String gen_str;
                if (is_other) {
                    String key;
                    switch (level) {
                        case ERROR:   key = "log_statistics.other_errors"; break;
                        case WARNING: key = "log_statistics.other_warnings"; break;
                        default:      key = "log_statistics.other";
                    }
                    gen_str = i18n.getLabel(key) + " ";
                } else {
                    gen_str = gen + " ";
                }
                int len = gen_str.length() + cntStr.length();
                StringBuilder sb = is_other ? other : stats;
                sb.append(gen_str);
                if (len < dots.length() - 3) {
                    sb.append(dots.substring(len));
                } else {
                    sb.append("...");
                }
                sb.append(cntStr).append("\n");
            }
        }
        stats.append(other);
        
        String total = totalLabel + ": " + totalCnt;
        stats.append(space.substring(total.length())).append(total).append("\n");
        
        if (stats.length() > 0) {
            String headline = i18n.getLabel(headkey);
            // progress.logHeader(2, headkey);
            progress.logText(headline, stats.toString());
        }
    }
    
    private static String getNodePath(String parentPath, DocmaNode node)
    {
        DocmaNode parent = node.getParent();
        if (parentPath == null) {
            if ((parent == null) || node.isDocumentRoot() || node.isRoot()) {
                return "";
            }
            parentPath = getNodePath(null, parent);
        }
        String prefix;
        if ((parent != null) && (parentPath.length() > 0)) {
            if (node.isSection()) {
                prefix = parentPath + " / ";
            } else {
                int pos = parent.getChildPos(node);
                prefix = parentPath + " / [" + pos + "] "; 
            }
        } else {
            prefix = "";
        }
        String title = node.getTitle();
        if ((title == null) || title.equals("")) {
            return prefix + node.getId();
        } else {
            return prefix + title;
        }
    }
    
    
    private static void callStartBatch(HTMLRuleContextImpl ctx,
                                       List<RuleConfig> ruleConfigs,
                                       Map<String, HTMLRule> ruleObjs)
                                       throws DocmaException
    {
        ctx.setContent(null);
        ctx.setNodeId(null);
        
        for (RuleConfig rc : ruleConfigs) {
            HTMLRule hr = ruleObjs.get(rc.getId());
            if (hr != null) {
                ctx.setActiveRule(rc);
                try {
                    hr.startBatch(ctx);
                } catch (Exception ex) {
                    ctx.log(LogLevel.ERROR, "Exception in startBatch() of rule " + rc.getId() + ": " + ex.getMessage());
                }
            }
        }
    }

    private static void callFinishBatch(HTMLRuleContextImpl ctx,
                                        List<RuleConfig> ruleConfigs,
                                        Map<String, HTMLRule> ruleObjs)
                                        throws DocmaException
    {
        ctx.setContent(null);
        ctx.setNodeId(null);
        
        for (RuleConfig rc : ruleConfigs) {
            HTMLRule hr = ruleObjs.get(rc.getId());
            if (hr != null) {
                ctx.setActiveRule(rc);
                try {
                    hr.finishBatch(ctx);
                } catch (Exception ex) {
                    ctx.log(LogLevel.INFO, "Exception in finishBatch() of rule " + rc.getId() + ": " + ex.getMessage());
                }
            }
        }
    }
    
    private static void callApply(StringBuilder content,
                                  String nodeId, 
                                  boolean allowAutoCorrect,
                                  HTMLRuleContextImpl ctx,
                                  List<RuleConfig> ruleConfigs,
                                  Map<String, HTMLRule> ruleObjs)
                                  throws DocmaException
    {
        ctx.setContent(content);
        ctx.setNodeId(nodeId);

        String contentStr = null;
        for (RuleConfig rc : ruleConfigs) {
            // Apply the rule to the content
            HTMLRule hr = ruleObjs.get(rc.getId());
            if (hr != null) {
                ctx.setActiveRule(rc);
                try {
                    if (contentStr == null) {  // initialize on first rule to be applied
                        contentStr = content.toString();
                    }
                    String res = hr.apply(contentStr, ctx);
                    if (allowAutoCorrect && (res != null)) {
                        contentStr = res;
                        content.replace(0, content.length(), res);
                    }
                } catch (Exception ex) {
                    ctx.log(LogLevel.ERROR, "Exception in applying rule " + rc.getId() + ": " + ex.getMessage());
                }
            }
        }
    }

    private static List<RuleConfig> getEnabledRuleConfigs(DocmaSession docmaSess, 
                                                          Map<Object, Object> props) 
                                                          throws DocmaException
    {
        RulesManager rm = docmaSess.getRulesManager();
        RuleConfig[] allRules = rm.getAllRules();
        List<RuleConfig> result = new ArrayList<RuleConfig>();

        for (RuleConfig rc : allRules) {
            try {
                if (rc.isRuleEnabled() && rc.isApplicableForStore(docmaSess.getStoreId())) {
                    String ruleId = rc.getId();
                    Object pobj = (props == null) ? null : props.get(ruleId);
                    String v = (pobj == null) ? "" : pobj.toString();
                    
                    // SPECIAL CASE: If this is the quick-links rule and no property 
                    // has been set, then set property based on user's profile setting.
                    if (v.equals("") && RulesManager.QUICK_LINKS_ID.equals(ruleId)) {
                        v = docmaSess.getUserProperty(DocmaConstants.PROP_USER_QUICKLINKS_ENABLED);
                        if ((v == null) || v.equals("")) {
                            v = "false";  // by default quick-links are disabled if no user property has been set
                        }
                    }
                    
                    // Skip rule if it is turned off by supplied properties
                    if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("off")) {
                        continue;   // skip this rule
                    }
                    
                    // Skip rule if it is turned off by default, and not turned on by property
                    if (! rc.isDefaultOn()) {   // rule is turned off by default
                        if (! (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on"))) {
                            continue;   // skip this rule
                        }
                    }

                    result.add(rc);
                }
            } catch (Exception ex) {
                Log.error(ex.getMessage());
            }
        }
        return result;
    }
    
    private static Map<String, HTMLRule> acquireRuleInstances(List<RuleConfig> configs)
    {
        Map<String, HTMLRule> result = new HashMap<String, HTMLRule>();
        for (RuleConfig rc : configs) {
            try {
                String rid = rc.getId();
                if (! result.containsKey(rid)) {
                    // Acquire rule instance
                    Object obj = rc.acquireRuleInstance();
                    if (obj instanceof HTMLRule) {
                        result.put(rid, (HTMLRule) obj);
                    }
                } else {
                    Log.error("Rule configuration with ID '" + rid + "' is not unique!");
                }
            } catch (Exception ex) {
                Log.error("Failed to acquire rule instance: " + ex.getMessage());
            }
        }
        return result;
    }

    private static void releaseRuleInstances(List<RuleConfig> configs, Map<String, HTMLRule> ruleObjs)
    {
        if (ruleObjs == null) {
            return;
        }
        for (RuleConfig rc : configs) {
            try {
                HTMLRule obj = ruleObjs.remove(rc.getId());
                if (obj != null) rc.releaseRuleInstance(obj);
            } catch (Exception ex) {
                Log.error("Failed to release rule instance: " + ex.getMessage());
            }
        }
    }

    private static HTMLRuleContextImpl createHTMLRuleContext(DocmaSession docmaSess, 
                                                             Map<Object, Object> props, 
                                                             boolean allowAutoCorrect, 
                                                             boolean isModeSave)
    {
        StoreConnectionImpl storeConn = (StoreConnectionImpl) docmaSess.getPluginStoreConnection();
        HTMLRuleContextImpl ctx = new HTMLRuleContextImpl(storeConn);
        if (props != null) {
            ctx.setProperties(props);
        }
        if (isModeSave) {
            ctx.setModeSave();
        } else {
            ctx.setModeCheck();
        }
        ctx.setAllowAutoCorrect(allowAutoCorrect);
        return ctx;
    }
    
    private static class NodeCounts 
    {
        private int skipped = 0;
        private int processed = 0;
        private int updated = 0;
        
        int getSkippedCount()
        {
            return skipped;
        }
        
        void increaseSkipped()
        {
            skipped++;
        }
        
        int getProcessedCount()
        {
            return processed;
        }
        
        void increaseProcessed()
        {
            processed++;
        }
        
        int getUpdatedCount()
        {
            return updated;
        }
        
        void increaseUpdated()
        {
            updated++;
        }
    }
    
    private static class LogCounts 
    {
        private String generator;
        private int errors = 0;
        private int warnings = 0;
        private int infos = 0;
        
        LogCounts(String generator)
        {
            this.generator = generator;
        }
        
        String getGenerator()
        {
            return generator;
        }
        
        int getErrorCount()
        {
            return errors;
        }
        
        void increaseErrors()
        {
            errors++;
        }
        
        int getWarningCount()
        {
            return warnings;
        }
        
        void increaseWarnings()
        {
            warnings++;
        }
        
        int getInfoCount()
        {
            return infos;
        }
        
        void increaseInfos()
        {
            infos++;
        }
    }
}
