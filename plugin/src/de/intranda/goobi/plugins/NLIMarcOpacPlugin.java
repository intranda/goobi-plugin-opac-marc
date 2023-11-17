package de.intranda.goobi.plugins;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;

import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.w3c.dom.Node;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;

import de.intranda.goobi.plugins.sru.SRUHelper;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import de.unigoettingen.sub.search.opac.ConfigOpacDoctype;
import io.goobi.workflow.api.connection.HttpUtils;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;

@PluginImplementation
@Log4j2
public class NLIMarcOpacPlugin extends GbvMarcSruImport implements IOpacPlugin {

    private static final long serialVersionUID = -2821112693238589678L;

    public NLIMarcOpacPlugin() {
        super();
        super.setVersion("1.2");
        super.setMarcNamespace(null);

    }

    @Override
    public Fileformat search(String inSuchfeld, String searchValue, ConfigOpacCatalogue cat, Prefs inPrefs) throws Exception {
        coc = cat;
        String catalogue = cat.getAddress().replace("{identifier}", searchValue);

        String value = HttpUtils.getStringFromUrl(catalogue);

        InputStream targetStream = new ByteArrayInputStream(value.getBytes());

        Object object = Configuration.defaultConfiguration().jsonProvider().parse(targetStream, "UTF-8");
        List<Object> records = JsonPath.read(object, "bib");
        if (records != null && !records.isEmpty()) {

            value = JsonPath.read(records.get(0), "anies[0]");

        } else {
            return null;
        }

        value = Normalizer.normalize(value, Normalizer.Form.NFC);
        Node node = SRUHelper.parseNliResult(this, value);
        if (node == null) {
            return null;
        }
        Fileformat ff = SRUHelper.parseMarcFormat(node, inPrefs);
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
            } catch (TypeNotAllowedForParentException | TypeNotAllowedAsChildException e) {
                log.error(e);
            }
        }

        return ff;

    }

    @Override
    public String getTitle() {
        return "NLI-MARC";
    }

    @Override
    public String getDescription() {
        return getTitle();
    }
}
