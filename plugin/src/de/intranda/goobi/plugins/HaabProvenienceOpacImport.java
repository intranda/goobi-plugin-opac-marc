package de.intranda.goobi.plugins;

import java.io.StringReader;

import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;

import de.intranda.goobi.plugins.sru.SRUHelper;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import lombok.Getter;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;

@PluginImplementation
public class HaabProvenienceOpacImport extends GbvMarcSruImport implements IOpacPlugin {

    private static final long serialVersionUID = 1634138719131116342L;

    private static Namespace pica = Namespace.getNamespace("pica", "info:srw/schema/5/picaXML-v1.0");

    private Element picaRecord;

    @Override
    public PluginType getType() {
        return PluginType.Opac;
    }

    @Getter
    private String title = "HaabProvenienceOpac";



    public HaabProvenienceOpacImport() {
        super();
        super.setVersion("1.2");
        super.setIdentifierSearchFieldPrefix("pica.epn");
        super.setSruSchema("picaxml");

    }

    @Override

    public Fileformat search(String field, String searchValue, ConfigOpacCatalogue coc, Prefs inPrefs) throws Exception {
        SRUHelper.setMarcNamespace(pica);
        String value = SRUHelper.search("http://sru.k10plus.de/opac-de-32", sruSchema, "pica.epn", searchValue, packing, version);
        if (StringUtils.isNotBlank(value)) {
            Document document = SRUHelper.getSaxBuilder(true).build(new StringReader(value), "utf-8");
            picaRecord = SRUHelper.getRecordWithoutSruHeader(document, coc.getBeautifySetList());

        }
        return null;
    }

    public Element getPicaRecord() {
        return picaRecord;
    }
}
