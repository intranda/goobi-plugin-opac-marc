package de.intranda.goobi.plugins;

import org.goobi.production.plugin.interfaces.IOpacPlugin;

import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class K10plusOpacPlugin extends GbvMarcSruImport implements IOpacPlugin {

    private static final long serialVersionUID = -4318557950759331407L;


    public K10plusOpacPlugin() {
        super();
        super.setVersion("1.2");
        super.setIdentifierSearchFieldPrefix("pica.ppn");
        super.setMarcNamespace(null);
        super.setSruSchema("marcxml");
    }

    @Override
    public String getTitle() {
        return "K10plus";
    }


    @Override
    public String getDescription() {
        return "K10plus";
    }
}
