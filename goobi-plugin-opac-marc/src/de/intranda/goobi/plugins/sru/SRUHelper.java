package de.intranda.goobi.plugins.sru;

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

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import com.googlecode.fascinator.redbox.sru.SRUClient;

import de.intranda.goobi.plugins.GbvMarcSruImport;
//import de.intranda.goobi.plugins.SwbMarcSruImport;
import de.intranda.ugh.extension.MarcFileformat;
import de.sub.goobi.helper.Helper;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.XStream;

public class SRUHelper {
    private static final Namespace SRW = Namespace.getNamespace("srw", "http://www.loc.gov/zing/srw/");
    private static Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

    public static void setMarcNamespace(Namespace marc) {
        MARC = marc;
    }

    public static String search(String catalogue, String schema, String searchField, String searchValue, String packing, String version) {
        SRUClient client;
        try {
            client = new SRUClient(catalogue, schema, packing, version);
            return client.getSearchResponse(searchField + "=" + searchValue);
        } catch (MalformedURLException e) {
        }
        return "";
    }

    public static Node parseHaabResult(GbvMarcSruImport opac, String catalogue, String schema, String searchField, String searchValue,
            String resultString, String packing, String version) throws IOException, JDOMException, ParserConfigurationException {
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
        builder.setFeature("http://xml.org/sax/features/validation", false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = builder.build(new StringReader(resultString), "utf-8");
        Element record = getRecordWithoutSruHeader(doc);
        if (record == null) {
            opac.setHitcount(0);
            return null;
        }
        opac.setHitcount(1);
        boolean isPeriodical = false;
        boolean isManuscript = false;
        boolean isCartographic = false;
        boolean isMultiVolume = false;
        boolean isFSet = false;

        String anchorPpn = null;
        String otherAnchorPpn = null;
        String otherAnchorEpn = null;

        String otherPpn = null;
        String currentEpn = null;
        String otherEpn = null;
        boolean foundMultipleEpns = false;

        // generate an answer document
        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
        org.w3c.dom.Document answer = docBuilder.newDocument();
        org.w3c.dom.Element collection = answer.createElement("collection");
        answer.appendChild(collection);

        List<Element> data = record.getChildren();
        for (Element el : data) {
            if (el.getName().equalsIgnoreCase("leader")) {
                String value = el.getText();
                char c6 = value.toCharArray()[6];
                char c7 = value.toCharArray()[7];
                char c19 = value.toCharArray()[19];
                if (c6 == 'a' && (c7 == 's' || c7 == 'd')) {
                    isPeriodical = true;
                } else if (c6 == 't') {
                    isManuscript = true;
                } else if (c6 == 'e') {
                    isCartographic = true;
                }
                if (c19 == 'b' || c19 == 'c') {
                    isFSet = true;
                }

            }
            if (el.getName().equalsIgnoreCase("datafield")) {
                String tag = el.getAttributeValue("tag");
                List<Element> subfields = el.getChildren();
                for (Element sub : subfields) {
                    String code = sub.getAttributeValue("code");
                    // anchor identifier
                    if (tag.equals("773") && code.equals("w")) {
                        isMultiVolume = true;
                        anchorPpn = sub.getText().replaceAll("\\(.+\\)", "");
                    } else if (tag.equals("800") && code.equals("w")) {
                        isMultiVolume = true;
                        anchorPpn = sub.getText().replaceAll("\\(.+\\)", "");
                    } else if (isManuscript && tag.equals("810") && code.equals("w")) {
                        isMultiVolume = true;
                        anchorPpn = sub.getText().replaceAll("\\(.+\\)", "");
                    } else if (tag.equals("830") && code.equals("w")) {
                        if (isCartographic || (isFSet && anchorPpn == null)) {
                            isMultiVolume = true;
                            anchorPpn = sub.getText().replaceAll("\\(.+\\)", "");
                        }
                    } else if (tag.equals("776") && code.equals("w")) {
                        if (otherPpn == null) {
                            // found first/only occurrence
                            otherPpn = sub.getText().replaceAll("\\(.+\\)", "");
                        } else {
                            otherPpn = null;
                            foundMultipleEpns = true;
                        }

                    } else if (tag.equals("954") && code.equals("b")) {
                        if (searchField.equals("pica.epn")) {
                            // remove wrong epns
                            currentEpn = sub.getText().replaceAll("\\(.+\\)", "");
                            if (!searchValue.trim().equals(currentEpn)) {
                                sub.setAttribute("code", "invalid");
                                for (Element exemplarData : subfields) {
                                    if (exemplarData.getAttributeValue("code").equals("d")) {
                                        exemplarData.setAttribute("code", "invalid");
                                    }
                                }
                            }
                        } else {
                            if (currentEpn == null) {
                                currentEpn = sub.getText().replaceAll("\\(.+\\)", "");
                            } else {
                                foundMultipleEpns = true;
                            }
                        }
                    }
                }
            }
        }

        //  search for pica.zdb for periodca
        // get digital epn from digital ppn record
        if (otherPpn != null) {
            String otherResult = SRUHelper.search(catalogue, schema, isPeriodical ? "pica.zdb" : "pica.ppn", otherPpn, packing, version);
            Document otherDocument = new SAXBuilder().build(new StringReader(otherResult), "utf-8");
            if (otherDocument != null) {
                Element otherRecord = getRecordWithoutSruHeader(otherDocument);
                if (otherRecord == null) {
                    Helper.setFehlerMeldung("import_OtherEPNNotFound");
                } else {

                    List<Element> controlList = otherRecord.getChildren("controlfield", MARC);
                    for (Element field : controlList) {
                        if (field.getAttributeValue("tag").equals("001")) {
                            otherPpn = field.getText();
                        }
                    }

                    List<Element> fieldList = otherRecord.getChildren("datafield", MARC);
                    for (Element field : fieldList) {
                        String tag = field.getAttributeValue("tag");
                        List<Element> subfields = field.getChildren();
                        for (Element sub : subfields) {
                            String code = sub.getAttributeValue("code");
                            // anchor identifier
                            if (tag.equals("773") && code.equals("w")) {
                                otherAnchorPpn = sub.getText().replaceAll("\\(.+\\)", "");
                            } else if (tag.equals("800") && code.equals("w")) {
                                otherAnchorPpn = sub.getText().replaceAll("\\(.+\\)", "");
                            } else if (isManuscript && tag.equals("810") && code.equals("w")) {
                                otherAnchorPpn = sub.getText().replaceAll("\\(.+\\)", "");
                            } else if (isCartographic && tag.equals("830") && code.equals("w")) {
                                otherAnchorPpn = sub.getText().replaceAll("\\(.+\\)", "");
                            } else if (tag.equals("954") && code.equals("b")) {
                                if (otherEpn == null) {
                                    otherEpn = sub.getText().replaceAll("\\(.+\\)", "");
                                } else {
                                    foundMultipleEpns = true;
                                    otherEpn = null;
                                }
                            }

                        }
                    }
                }
                if (otherPpn != null) {
                    Element datafield = new Element("datafield", MARC);
                    datafield.setAttribute("tag", "ppnDigital");
                    datafield.setAttribute("ind1", "");
                    datafield.setAttribute("ind2", "");

                    Element subfield = new Element("subfield", MARC);
                    subfield.setAttribute("code", "a");
                    subfield.setText(otherPpn);
                    datafield.addContent(subfield);
                    data.add(datafield);
                }
                if (otherEpn != null && !foundMultipleEpns) {
                    Element datafield = new Element("datafield", MARC);
                    datafield.setAttribute("tag", "epnDigital");
                    datafield.setAttribute("ind1", "");
                    datafield.setAttribute("ind2", "");

                    Element subfield = new Element("subfield", MARC);
                    subfield.setAttribute("code", "a");
                    subfield.setText(otherEpn);
                    datafield.addContent(subfield);
                    data.add(datafield);
                }
            }
        }
        org.w3c.dom.Element marcRecord = getRecord(answer, data, opac);

        if (isMultiVolume) {
            // get anchor record
            String anchorResult = SRUHelper.search(catalogue, schema, "pica.ppn", anchorPpn, packing, version);
            Document anchorDoc = new SAXBuilder().build(new StringReader(anchorResult), "utf-8");

            Element anchorRecord = getRecordWithoutSruHeader(anchorDoc);

            if (anchorRecord != null) {
                List<Element> anchorData = anchorRecord.getChildren();

                // get EPN/PPN digital for anchor
                String otherAnchorResult = SRUHelper.search(catalogue, schema, isPeriodical ? "pica.zdb" : "pica.ppn", otherAnchorPpn, packing,
                        version);
                Document otherAnchorDoc = new SAXBuilder().build(new StringReader(otherAnchorResult), "utf-8");
                Element otherAnchorRecord = getRecordWithoutSruHeader(otherAnchorDoc);

                if (otherAnchorRecord == null) {
                    Helper.setFehlerMeldung("import_OtherEPNNotFound");
                } else {

                    List<Element> controlList = otherAnchorRecord.getChildren("controlfield", MARC);
                    for (Element field : controlList) {
                        if (field.getAttributeValue("tag").equals("001")) {
                            otherAnchorPpn = field.getText();
                        }
                    }

                    List<Element> fieldList = otherAnchorRecord.getChildren("datafield", MARC);
                    for (Element field : fieldList) {
                        if (field.getAttributeValue("tag").equals("954")) {
                            List<Element> subfields = field.getChildren();
                            for (Element sub : subfields) {
                                String code = sub.getAttributeValue("code");
                                if (code.equals("b")) {
                                    if (otherAnchorEpn == null) {
                                        otherAnchorEpn = sub.getText().replaceAll("\\(.+\\)", "");
                                    } else {
                                        foundMultipleEpns = true;
                                    }
                                }
                            }
                        }
                    }

                    if (otherAnchorPpn != null) {
                        Element datafield = new Element("datafield", MARC);
                        datafield.setAttribute("tag", "ppnDigital");
                        datafield.setAttribute("ind1", "");
                        datafield.setAttribute("ind2", "");

                        Element subfield = new Element("subfield", MARC);
                        subfield.setAttribute("code", "a");
                        subfield.setText(otherAnchorPpn);
                        datafield.addContent(subfield);
                        anchorData.add(datafield);
                    }

                    if (otherAnchorEpn != null && !foundMultipleEpns) {
                        Element datafield = new Element("datafield", MARC);
                        datafield.setAttribute("tag", "epnDigital");
                        datafield.setAttribute("ind1", "");
                        datafield.setAttribute("ind2", "");

                        Element subfield = new Element("subfield", MARC);
                        subfield.setAttribute("code", "a");
                        subfield.setText(otherAnchorEpn);
                        datafield.addContent(subfield);
                        anchorData.add(datafield);
                    }
                }
                org.w3c.dom.Element anchorMarcRecord = getRecord(answer, anchorData, opac);

                collection.appendChild(anchorMarcRecord);
            }

        }

        if (foundMultipleEpns) {
            Helper.setFehlerMeldung("import_foundMultipleEPNs");
        }

        collection.appendChild(marcRecord);
        return answer.getDocumentElement();
    }

    public static Node parseGbvResult(GbvMarcSruImport opac, String catalogue, String schema, String searchField, String resultString, String packing,
            String version) throws IOException, JDOMException, ParserConfigurationException {
        // removed validation against external dtd
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);

        builder.setFeature("http://xml.org/sax/features/validation", false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = builder.build(new StringReader(resultString), "utf-8");
        // srw:searchRetrieveResponse
        Element record = getRecordWithoutSruHeader(doc);
        if (record == null) {
            opac.setHitcount(0);
            return null;
        } else {
            opac.setHitcount(1);
            // generate an answer document
            DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            org.w3c.dom.Document answer = docBuilder.newDocument();
            org.w3c.dom.Element collection = answer.createElement("collection");
            answer.appendChild(collection);

            boolean isMultiVolume = false;
            String anchorIdentifier = "";
            List<Element> data = record.getChildren();

            for (Element el : data) {
                if (el.getName().equalsIgnoreCase("datafield")) {
                    String tag = el.getAttributeValue("tag");
                    List<Element> subfields = el.getChildren();
                    for (Element sub : subfields) {
                        String code = sub.getAttributeValue("code");
                        // anchor identifier
                        if (tag.equals("773") && code.equals("w")) {
                            isMultiVolume = true;
                            anchorIdentifier = sub.getText().replaceAll("\\(.+\\)", "");
                        }
                    }
                }
            }

            org.w3c.dom.Element marcRecord = getRecord(answer, data, opac);

            if (isMultiVolume) {
                // TODO
                String anchorResult = SRUHelper.search(catalogue, schema, searchField, anchorIdentifier, packing, version);
                Document anchorDoc = new SAXBuilder().build(new StringReader(anchorResult), "utf-8");

                Element anchorRecord = getRecordWithoutSruHeader(anchorDoc);
                if (anchorRecord != null) {
                    List<Element> anchorData = anchorRecord.getChildren();
                    org.w3c.dom.Element anchorMarcRecord = getRecord(answer, anchorData, opac);

                    collection.appendChild(anchorMarcRecord);
                }

            }
            collection.appendChild(marcRecord);
            return answer.getDocumentElement();
        }

    }

    public static Element getRecordWithoutSruHeader(Document document) {
        Element root = document.getRootElement();
        // <srw:records>
        Element srw_records = root.getChild("records", SRW);
        // <srw:record>
        if (srw_records == null) {
            return null;
        }
        List<Element> srw_recordList = srw_records.getChildren("record", SRW);

        // <srw:recordData>
        if (srw_recordList == null || srw_recordList.isEmpty()) {
            return null;
        }
        Element recordData = srw_recordList.get(0).getChild("recordData", SRW);

        Element record = recordData.getChild("record", MARC);
        return record;
    }

    public static Fileformat parseMarcFormat(Node marc, Prefs prefs, String epn) throws ReadException, PreferencesException,
    TypeNotAllowedForParentException {

        MarcFileformat pp = new MarcFileformat(prefs);
        pp.read(marc);
        DigitalDocument dd = pp.getDigitalDocument();
        Fileformat ff = new XStream(prefs);
        ff.setDigitalDocument(dd);
        /* BoundBook hinzufügen */
        DocStructType dst = prefs.getDocStrctTypeByName("BoundBook");
        DocStruct dsBoundBook = dd.createDocStruct(dst);
        dd.setPhysicalDocStruct(dsBoundBook);

        return ff;

    }

    private static org.w3c.dom.Element getRecord(org.w3c.dom.Document answer, List<Element> data, IOpacPlugin plugin) {
        org.w3c.dom.Element marcRecord = answer.createElement("record");
        // fix for wrong leader in SWB
        org.w3c.dom.Element leader = null;
        String author = "";
        String title = "";
        for (Element datafield : data) {
            if (datafield.getName().equals("leader") && leader == null) {
                leader = answer.createElement("leader");
                marcRecord.appendChild(leader);

                Text text = answer.createTextNode(datafield.getText());
                leader.appendChild(text);

                // get the leader field as a datafield
                org.w3c.dom.Element leaderDataField = answer.createElement("datafield");
                leaderDataField.setAttribute("tag", "leader");
                leaderDataField.setAttribute("ind1", " ");
                leaderDataField.setAttribute("ind2", " ");

                org.w3c.dom.Element subfield = answer.createElement("subfield");
                leaderDataField.appendChild(subfield);
                subfield.setAttribute("code", "a");
                Text dataFieldtext = answer.createTextNode(datafield.getText());
                subfield.appendChild(dataFieldtext);
                marcRecord.appendChild(leaderDataField);

            } else if (datafield.getName().equals("controlfield")) {
                org.w3c.dom.Element field = answer.createElement("controlfield");

                Text text = answer.createTextNode(datafield.getText());
                field.appendChild(text);

                String tag = datafield.getAttributeValue("tag");
                field.setAttribute("tag", tag);
                marcRecord.appendChild(field);

                // get the controlfields as datafields
                org.w3c.dom.Element leaderDataField = answer.createElement("datafield");
                leaderDataField.setAttribute("tag", tag);
                leaderDataField.setAttribute("ind1", " ");
                leaderDataField.setAttribute("ind2", " ");

                org.w3c.dom.Element subfield = answer.createElement("subfield");
                leaderDataField.appendChild(subfield);
                subfield.setAttribute("code", "a");
                Text dataFieldtext = answer.createTextNode(datafield.getText());
                subfield.appendChild(dataFieldtext);
                marcRecord.appendChild(leaderDataField);

            } else if (datafield.getName().equals("datafield")) {
                String tag = datafield.getAttributeValue("tag");
                String ind1 = datafield.getAttributeValue("ind1");
                String ind2 = datafield.getAttributeValue("ind2");

                org.w3c.dom.Element field = answer.createElement("datafield");
                marcRecord.appendChild(field);

                field.setAttribute("tag", tag);
                field.setAttribute("ind1", ind1);
                field.setAttribute("ind2", ind2);
                List<Element> subfields = datafield.getChildren();

                for (Element sub : subfields) {
                    org.w3c.dom.Element subfield = answer.createElement("subfield");
                    field.appendChild(subfield);
                    String code = sub.getAttributeValue("code");
                    subfield.setAttribute("code", code);
                    Text text = answer.createTextNode(sub.getText());
                    subfield.appendChild(text);

                    if (tag.equals("100") && code.equals("a")) {
                        author = sub.getText();
                    }

                    // main title, create sorting title
                    if (tag.equals("245") && code.equals("a")) {
                        org.w3c.dom.Element sorting = answer.createElement("subfield");
                        field.appendChild(sorting);
                        sorting.setAttribute("code", "x");
                        String subtext = sub.getText();
                        if (!ind2.trim().isEmpty()) {
                            int numberOfNonfillingCharacter = new Integer(ind2).intValue();
                            subtext = subtext.substring(numberOfNonfillingCharacter);
                        }
                        title = subtext;
                        Text sortingtext = answer.createTextNode(subtext);
                        sorting.appendChild(sortingtext);

                    }
                }
            }
        }
        plugin.setAtstsl(plugin.createAtstsl(title, author));
        return marcRecord;
    }
}
