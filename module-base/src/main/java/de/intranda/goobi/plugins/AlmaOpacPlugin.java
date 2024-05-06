package de.intranda.goobi.plugins;

import org.goobi.production.plugin.interfaces.IOpacPlugin;

import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class AlmaOpacPlugin extends GbvMarcSruImport implements IOpacPlugin {

    private static final long serialVersionUID = -7827690324649388623L;


    public AlmaOpacPlugin() {
        super();
        super.setVersion("1.2");
        super.setIdentifierSearchFieldPrefix("alma.local_control_field_009");
        super.setMarcNamespace(null);
        super.setSruSchema("marcxml");
    }

    @Override
    public String getTitle() {
        return "ALMA-MARC";
    }


    @Override
    public String getDescription() {
        return "ALMA-MARC";
    }
}
