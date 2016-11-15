package de.intranda.goobi.plugins;

import org.goobi.production.plugin.interfaces.IOpacPlugin;

import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;

@PluginImplementation
public class AlmaOpacPlugin extends GbvMarcSruImport implements IOpacPlugin {

    public AlmaOpacPlugin() {
        super();
        super.setVersion("1.2");
        super.setIdentifierSearchFieldPrefix("other_system_number");
        super.setMarcNamespace(null);
    }
    
//    @Override
//    public Fileformat search(String inSuchfeld, String searchValue, ConfigOpacCatalogue cat, Prefs inPrefs) throws Exception {
//        
//        return super.search(inSuchfeld, "other_system_number=" + searchValue, cat, inPrefs);
//    }
    
    public String getTitle() {
        return "ALMA-MARC";
    }

    
    public String getDescription() {
        return "ALMA-MARC";
    }
}
