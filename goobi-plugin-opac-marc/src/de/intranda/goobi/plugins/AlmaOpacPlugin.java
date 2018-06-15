package de.intranda.goobi.plugins;

import org.goobi.production.plugin.interfaces.IOpacPlugin;

import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class AlmaOpacPlugin extends GbvMarcSruImport implements IOpacPlugin {

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
