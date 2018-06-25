package de.intranda.goobi.plugins;
/***************************************************************
 * Copyright notice
 *
 * (c) 2018 Robert Sehr <robert.sehr@intranda.com>
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

import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.w3c.dom.Node;

import de.intranda.goobi.plugins.sru.SRUHelper;
import de.sub.goobi.helper.UghHelper;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import lombok.Getter;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;

@PluginImplementation

public class HaabMarcSruOpacImport extends GbvMarcSruImport implements IOpacPlugin {

    //  1.) Suche nach EPN
    //  2.) Suche nach zweiten Datensatz durch PPN Suche aus 776$w
    //  3.) Suche nach zweiter EPN aus 954$b
    //  4.) Warnung, falls 954$b mehrfach vorkommt
    //  5.) ATS generieren
    // TODO 6.) Mehrbändige Werke


    @Getter
    private String title = "HaabMarcSru";

    public HaabMarcSruOpacImport() {
        super();
        super.setVersion("1.2");
        super.setIdentifierSearchFieldPrefix("pica.epn");
        super.setSruSchema("marcxml");
    }


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
        } else {
            searchField = inSuchfeld;
        }

        SRUHelper.setMarcNamespace(getMarcNamespace());
        String value = SRUHelper.search(catalogue, sruSchema, searchField, searchValue, packing, version);
        Node node = SRUHelper.parseHaabResult(this, catalogue, sruSchema, searchField, searchValue, value, packing, version);
        if (node == null) {
            return null;
        }

        Fileformat ff = SRUHelper.parseMarcFormat(node, inPrefs, searchValue);
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


    @Override
    public String createAtstsl(String myTitle, String autor) {
        String titleValue = "";
        if (myTitle != null && !myTitle.isEmpty()) {
            if (myTitle.contains(" ")) {
                titleValue = myTitle.substring(0, myTitle.indexOf(" "));
            } else {
                titleValue = myTitle;
            }
        }
        titleValue = titleValue.toLowerCase();
        if (titleValue.length() > 6) {
            atstsl = titleValue.substring(0, 6);
        } else {
            atstsl = titleValue;
        }
        atstsl = UghHelper.convertUmlaut(atstsl);
        atstsl = atstsl.replaceAll("[\\W]", "");
        return atstsl;
    }
}
