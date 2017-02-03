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

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.XStream;

import com.googlecode.fascinator.redbox.sru.SRUClient;

import de.intranda.goobi.plugins.GbvMarcSruImport;
//import de.intranda.goobi.plugins.SwbMarcSruImport;
import de.intranda.ugh.extension.MarcFileformat;

public class SRUHelper {
    private static final Namespace SRW = Namespace.getNamespace("srw", "http://www.loc.gov/zing/srw/");
    private static Namespace MARC = Namespace.getNamespace("marc", "http://www.loc.gov/MARC21/slim");

    public static void setMarcNamespace(Namespace marc) {
        MARC = marc;
    }

    public static String search(String catalogue, String searchField, String searchValue, String packing, String version) {
        SRUClient client;
        try {
            client = new SRUClient(catalogue, "marcxml", packing, version);
            return client.getSearchResponse(searchField + "=" + searchValue);
        } catch (MalformedURLException e) {
        }
        return "";
    }

    public static Node parseGbvResult(GbvMarcSruImport opac, String catalogue, String resultString, String packing, String version)
            throws IOException, JDOMException, ParserConfigurationException {
        // removed validation against external dtd
        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);

        builder.setFeature("http://xml.org/sax/features/validation", false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Document doc = builder.build(new StringReader(resultString), "utf-8");
        // srw:searchRetrieveResponse
        Element root = doc.getRootElement();
        // <srw:records>
        Element srw_records = root.getChild("records", SRW);
        // <srw:record>
        List<Element> srw_recordList = srw_records.getChildren("record", SRW);
        // <srw:recordData>
        if (srw_recordList == null || srw_recordList.isEmpty()) {
            opac.setHitcount(0);
            return null;
        } else {
            opac.setHitcount(srw_recordList.size());
            Element recordData = srw_recordList.get(0).getChild("recordData", SRW);

            Element record = recordData.getChild("record", MARC);

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
                String anchorResult = SRUHelper.search(catalogue, "pica.ppn", anchorIdentifier, packing, version);
                Document anchorDoc = new SAXBuilder().build(new StringReader(anchorResult), "utf-8");

                // srw:searchRetrieveResponse
                Element anchorRoot = anchorDoc.getRootElement();
                // <srw:records>
                Element anchorSrw_records = anchorRoot.getChild("records", SRW);
                // <srw:record>
                Element anchorSrw_record = anchorSrw_records.getChild("record", SRW);
                // <srw:recordData>
                if (anchorSrw_record != null) {
                    Element anchorRecordData = anchorSrw_record.getChild("recordData", SRW);
                    Element anchorRecord = anchorRecordData.getChild("record", MARC);

                    List<Element> anchorData = anchorRecord.getChildren();
                    org.w3c.dom.Element anchorMarcRecord = getRecord(answer, anchorData, opac);

                    collection.appendChild(anchorMarcRecord);
                }

            }
            collection.appendChild(marcRecord);
            return answer.getDocumentElement();
        }

    }

//    public static Node parseSwbResult(SwbMarcSruImport opac, String catalogue, String resultString, String packing, String version)
//            throws IOException, JDOMException, ParserConfigurationException {
//        // removed validation against external dtd
//        SAXBuilder builder = new SAXBuilder(XMLReaders.NONVALIDATING);
//
//        builder.setFeature("http://xml.org/sax/features/validation", false);
//        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
//        builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
//        Document doc = builder.build(new StringReader(resultString), "utf-8");
//        // srw:searchRetrieveResponse
//        Element root = doc.getRootElement();
//        // <srw:records>
//        Element srw_records = root.getChild("records", SRW);
//        // <srw:record>
//        List<Element> srw_recordList = srw_records.getChildren("record", SRW);
//        // <srw:recordData>
//        if (srw_recordList == null || srw_recordList.isEmpty()) {
//            opac.setHitcount(0);
//            return null;
//        } else {
//            opac.setHitcount(srw_recordList.size());
//            Element recordData = srw_recordList.get(0).getChild("recordData", SRW);
//
//            Element record = recordData.getChild("record");
//
//            // generate an answer document
//            DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
//            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
//            org.w3c.dom.Document answer = docBuilder.newDocument();
//            org.w3c.dom.Element collection = answer.createElement("collection");
//            answer.appendChild(collection);
//
//            boolean isMultiVolume = false;
//            String anchorIdentifier = "";
//            List<Element> data = record.getChildren();
//
//            for (Element el : data) {
//                if (el.getName().equalsIgnoreCase("datafield")) {
//                    String tag = el.getAttributeValue("tag");
//                    List<Element> subfields = el.getChildren();
//                    for (Element sub : subfields) {
//                        String code = sub.getAttributeValue("code");
//                        // anchor identifier
//                        if (tag.equals("773") && code.equals("w")) {
//                            isMultiVolume = true;
//                            anchorIdentifier = sub.getText().replaceAll("\\(.+\\)", "");
//                        }
//                    }
//                }
//            }
//
//            org.w3c.dom.Element marcRecord = getRecord(answer, data, opac);
//
//            if (isMultiVolume) {
//                String anchorResult = SRUHelper.search(catalogue, "pica.ppn", anchorIdentifier, packing, version);
//                Document anchorDoc = new SAXBuilder().build(new StringReader(anchorResult), "utf-8");
//
//                // srw:searchRetrieveResponse
//                Element anchorRoot = anchorDoc.getRootElement();
//                // <srw:records>
//                Element anchorSrw_records = anchorRoot.getChild("records", SRW);
//                // <srw:record>
//                Element anchorSrw_record = anchorSrw_records.getChild("record", SRW);
//                // <srw:recordData>
//                if (anchorSrw_record != null) {
//                    Element anchorRecordData = anchorSrw_record.getChild("recordData", SRW);
//                    Element anchorRecord = anchorRecordData.getChild("record", MARC);
//
//                    List<Element> anchorData = anchorRecord.getChildren();
//                    org.w3c.dom.Element anchorMarcRecord = getRecord(answer, anchorData, opac);
//
//                    collection.appendChild(anchorMarcRecord);
//                }
//
//            }
//            collection.appendChild(marcRecord);
//            return answer.getDocumentElement();
//        }
//
//    }

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
