/*
 *  This file is part of the Alchemy project - http://al.chemy.org
 * 
 *  Copyright (c) 2007-2010 Karl D.D. Willis
 * 
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  Alchemy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with Alchemy.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package org.alchemy.core;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

/** Custom separator to fit the subtoolbar */
public class AlcSubSeparator extends JLabel {

    public AlcSubSeparator() {

        this.setIcon(AlcUtil.getImageIcon("sub-separator.png"));
        //this.setVerticalAlignment(SwingConstants.BOTTOM);
        // Top Left Bottom Right
        this.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
    }
}

