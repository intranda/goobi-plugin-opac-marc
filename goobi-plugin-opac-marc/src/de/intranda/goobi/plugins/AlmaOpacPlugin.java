package de.intranda.goobi.plugins;

import org.goobi.production.plugin.interfaces.IOpacPlugin;

import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class AlmaOpacPlugin extends GbvMarcSruImport implements IOpacPlugin {

    public AlmaOpacPlugin() {
        super();
        super.setVersion("1.2");
        super.setIdentifierSearchFieldPrefix("other_system_number");
        super.setMarcNamespace(null);
    }
    
    public String getTitle() {
        return "ALMA-MARC";
    }

    
    public String getDescription() {
        return "ALMA-MARC";
    }
}
