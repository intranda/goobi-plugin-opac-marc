package de.intranda.goobi.plugins;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.goobi.production.plugin.interfaces.IOpacPluginVersion2;

import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class AlmaOpacPluginStoreMarc extends GbvMarcSruImport implements IOpacPluginVersion2 {

    private static final long serialVersionUID = -7827690324649388623L;

    public AlmaOpacPluginStoreMarc() {
        super();
        saveMarcRecord = true;
        super.setVersion("1.2");
        super.setIdentifierSearchFieldPrefix("alma.local_control_field_009");
        super.setMarcNamespace(null);
        super.setSruSchema("marcxml");
    }

    @Override
    public String getTitle() {
        return "ALMA-SAVE-MARC";
    }

    @Override
    public String getDescription() {
        return "ALMA-SAVE-MARC";
    }

    @Override
    public Map<String, String> getRawDataAsString() {
        return null;
    }

    @Override
    public List<Path> getRecordPathList() {
        return pathToMarcRecord;
    }
}
