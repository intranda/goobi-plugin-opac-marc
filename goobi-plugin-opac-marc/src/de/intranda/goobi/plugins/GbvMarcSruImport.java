/***************************************************************
 * Copyright notice
 *
 * (c) 2016 Robert Sehr <robert.sehr@intranda.com>
 *
 * All rights reserved
 *
 * This file is part of the Goobi project. The Goobi project is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * The GNU General Public License can be found at
 * http://www.gnu.org/copyleft/gpl.html.
 *
 * This script is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * This copyright notice MUST APPEAR in all copies of this file!
 ***************************************************************/

package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.StringTokenizer;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.log4j.Logger;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.w3c.dom.Node;

import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import de.intranda.goobi.plugins.sru.SRUHelper;
import de.sub.goobi.helper.UghHelper;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;

@PluginImplementation
public class GbvMarcSruImport implements IOpacPlugin {
    private static final Logger logger = Logger.getLogger(GbvMarcSruImport.class);

    private int hitcount;
    private String gattung = "Aa";
    private String atstsl;
    private ConfigOpacCatalogue coc;

    @Override
    public Fileformat search(String inSuchfeld, String searchValue, ConfigOpacCatalogue cat, Prefs inPrefs) throws Exception {
        coc = cat;
        String searchField = "";
        String catalogue = cat.getAddress();

        if (inSuchfeld.equals("12")) {
            searchField = "pica.ppn";
        } else if (inSuchfeld.equals("8000")) {
            searchField = "pica.epn";
        } else if (inSuchfeld.equals("7")) {
            searchField = "pica.isb";
        } else if (inSuchfeld.equals("8")) {
            searchField = "pica.iss";
        }

        String value = SRUHelper.search(catalogue, searchField, searchValue);
        Node node = SRUHelper.parseGbvResult(this, catalogue, value);
        if (node == null) {
            return null;
        }
        Fileformat ff = SRUHelper.parseMarcFormat(node, inPrefs, searchValue);
        gattung = ff.getDigitalDocument().getLogicalDocStruct().getType().getName();
        return ff;
    }

    /* (non-Javadoc)
     * @see de.sub.goobi.Import.IOpac#getHitcount()
     */
    @Override
    public int getHitcount() {
        return this.hitcount;
    }

    public void setHitcount(int hitcount) {
        this.hitcount = hitcount;
    }

    public void setGattung(String gattung) {
        this.gattung = gattung;
    }

    public String getGattung() {
        return gattung;
    }


    @Override
    public String getAtstsl() {
        return this.atstsl;
    }

    public void setAtstsl(String atstsl) {
        this.atstsl = atstsl;
    }

    @Override
    public PluginType getType() {
        return PluginType.Opac;
    }

    @Override
    public String getTitle() {
        return "GBV-MARC";
    }

    @Override
    public String getDescription() {
        return "GBV-MARC";
    }

    @Override
    public ConfigOpacDoctype getOpacDocType() {
        try {
            ConfigOpac co = new ConfigOpac();
            ConfigOpacDoctype cod = co.getDoctypeByMapping(this.gattung, this.coc.getTitle());
            if (cod == null) {

                cod = new ConfigOpac().getAllDoctypes().get(0);
                this.gattung = cod.getMappings().get(0);

            }
            return cod;
        } catch (IOException e) {
            logger.error("OpacDoctype unknown", e);

            return null;
        }
    }

    public String createAtstsl(String title, String author) {
        StringBuilder result = new StringBuilder(8);
        if (author != null && author.trim().length() > 0) {
            result.append(author.length() > 4 ? author.substring(0, 4) : author);
            result.append(title.length() > 4 ? title.substring(0, 4) : title);
        } else {
            StringTokenizer titleWords = new StringTokenizer(title);
            int wordNo = 1;
            while (titleWords.hasMoreTokens() && wordNo < 5) {
                String word = titleWords.nextToken();
                switch (wordNo) {
                    case 1:
                        result.append(word.length() > 4 ? word.substring(0, 4) : word);
                        break;
                    case 2:
                    case 3:
                        result.append(word.length() > 2 ? word.substring(0, 2) : word);
                        break;
                    case 4:
                        result.append(word.length() > 1 ? word.substring(0, 1) : word);
                        break;
                }
                wordNo++;
            }
        }
        String res = UghHelper.convertUmlaut(result.toString()).toLowerCase();
        return res.replaceAll("[\\W]", ""); 
    }

}
