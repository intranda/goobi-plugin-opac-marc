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

package de.intranda.goobi.plugins;

import org.goobi.production.plugin.interfaces.IOpacPlugin;

import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class ZdbMarcSruImport  extends GbvMarcSruImport implements IOpacPlugin {

    public ZdbMarcSruImport() {
        super();
        super.setVersion("1.1");
        super.setIdentifierSearchFieldPrefix("idn");
        super.setSruSchema("MARC21-xml");

    }

    @Override
    public String getTitle() {
        return "ZDB-MARC";
    }


    @Override
    public String getDescription() {
        return "ZDB-MARC";
    }



}
