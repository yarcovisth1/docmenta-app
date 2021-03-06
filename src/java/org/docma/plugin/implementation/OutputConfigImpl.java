/*
 * OutputConfigImpl.java
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

import java.lang.reflect.Method;

import org.docma.plugin.OutputConfig;
import org.docma.app.DocmaOutputConfig;

/**
 *
 * @author MP
 */
public class OutputConfigImpl implements OutputConfig
{
    private final DocmaOutputConfig docmaOutConf;

    public OutputConfigImpl(DocmaOutputConfig docmaOutConf)
    {
        this.docmaOutConf = docmaOutConf;
    }

    DocmaOutputConfig getDocmaOutputConfig()
    {
        return docmaOutConf;
    }
    
    // ********* Interface OutputConfig (visible by plugins) **********

    public String getId() 
    {
        return docmaOutConf.getId();
    }

    public String getFormat() 
    {
        return docmaOutConf.getFormat();
    }

    public String getSubformat() 
    {
        return docmaOutConf.getSubformat();
    }

    
    public String getProperty(String propName) 
    {
        return docmaOutConf.getProperty(propName);
    }

}
