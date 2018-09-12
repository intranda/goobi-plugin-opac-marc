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

import java.text.Normalizer;
import java.util.StringTokenizer;

import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.jdom2.Namespace;
import org.w3c.dom.Node;

import de.intranda.goobi.plugins.sru.SRUHelper;
import de.sub.goobi.helper.UghHelper;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;

@PluginImplementation
public class GbvMarcSruImport implements IOpacPlugin {

    private int hitcount;
    protected String gattung = "Aa";
    protected String atstsl;
    protected ConfigOpacCatalogue coc;
    protected String sruSchema = "marcxml";

    protected String packing = null;
    protected String version = null;
    private String identifierSearchFieldPrefix = "pica.ppn";
    private Namespace marcNamespace = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");;

    @Override
    public Fileformat search(String inSuchfeld, String searchValue, ConfigOpacCatalogue cat, Prefs inPrefs) throws Exception {
        coc = cat;
        String searchField = "";
        String catalogue = cat.getAddress();

        if (inSuchfeld.equals("12")) {
            searchField = identifierSearchFieldPrefix;
        } else if (inSuchfeld.equals("8000")) {
            searchField = "pica.epn";
        } else if (inSuchfeld.equals("7")) {
            searchField = "pica.isb";
        } else if (inSuchfeld.equals("8")) {
            searchField = "pica.iss";
        } else {
            searchField = inSuchfeld;
        }

        SRUHelper.setMarcNamespace(marcNamespace);
        String value = SRUHelper.search(catalogue, sruSchema, searchField, searchValue, packing, version);
        Node node = SRUHelper.parseGbvResult(this, catalogue, sruSchema, searchField, value, packing, version);
        if (node == null) {
            return null;
        }
        Fileformat ff = SRUHelper.parseMarcFormat(node, inPrefs, searchValue);
        if (ff == null || ff.getDigitalDocument().getLogicalDocStruct() == null || ff.getDigitalDocument().getLogicalDocStruct().getType() == null) {
            return null;
        }
        gattung = ff.getDigitalDocument().getLogicalDocStruct().getType().getName();
        ConfigOpacDoctype codt = getOpacDocType();
        if (codt.isPeriodical()) {
            try {
                if (codt.getRulesetChildType() != null && !codt.getRulesetChildType().isEmpty()) {
                    DocStructType dstyvolume = inPrefs.getDocStrctTypeByName(codt.getRulesetChildType());
                    DocStruct dsvolume = ff.getDigitalDocument().createDocStruct(dstyvolume);
                    ff.getDigitalDocument().getLogicalDocStruct().addChild(dsvolume);
                } else {
                    DocStructType dstV = inPrefs.getDocStrctTypeByName("PeriodicalVolume");
                    DocStruct dsvolume = ff.getDigitalDocument().createDocStruct(dstV);
                    ff.getDigitalDocument().getLogicalDocStruct().addChild(dsvolume);
                }
            } catch (TypeNotAllowedForParentException e) {
                e.printStackTrace();
            } catch (TypeNotAllowedAsChildException e) {
                e.printStackTrace();
            }
        }

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

    @Override
    public String getGattung() {
        return gattung;
    }

    @Override
    public String getAtstsl() {
        return this.atstsl;
    }

    @Override
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

    public String getDescription() {
        return "GBV-MARC";
    }

    @Override
    public ConfigOpacDoctype getOpacDocType() {

        ConfigOpac co;
        ConfigOpacDoctype cod = null;

        co = ConfigOpac.getInstance();
        cod = co.getDoctypeByMapping(this.gattung, this.coc.getTitle());
        if (cod == null) {

            cod = co.getAllDoctypes().get(0);
            this.gattung = cod.getMappings().get(0);

        }

        return cod;

    }

    @Override
    public String createAtstsl(String title, String author) {
        title = Normalizer.normalize(title, Normalizer.Form.NFC);
        if (author != null) {
            author = Normalizer.normalize(author, Normalizer.Form.NFC);
        }

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

    public String getPacking() {
        return packing;
    }

    public void setPacking(String packing) {
        this.packing = packing;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getIdentifierSearchFieldPrefix() {
        return identifierSearchFieldPrefix;
    }

    public void setIdentifierSearchFieldPrefix(String identifierSearchFieldPrefix) {
        this.identifierSearchFieldPrefix = identifierSearchFieldPrefix;
    }

    public void setMarcNamespace(Namespace marcNamespace) {
        this.marcNamespace = marcNamespace;
    }

    public Namespace getMarcNamespace() {
        return marcNamespace;
    }

    public void setSruSchema(String sruSchema) {
        this.sruSchema = sruSchema;
    }

}
