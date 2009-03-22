/*
 *  This file is part of the Alchemy project - http://al.chemy.org
 * 
 *  Copyright (c) 2007-2009 Karl D.D. Willis
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

import java.awt.*;
import java.awt.print.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.Graphics2D;

// ITEXT
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.*;
import com.lowagie.text.xml.xmp.*;

import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.print.Printable;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import java.io.*;

// PDF READER
import com.sun.pdfview.*;

// JPEN
import jpen.*;
import jpen.event.PenListener;

/** 
 * The Alchemy canvas <br>
 * Stores all shapes created and handles all graphics related stuff<br>
 * Think saving pdfs, printing, and of course displaying! 
 */
public class AlcCanvas extends JPanel implements AlcConstants, MouseListener, MouseMotionListener, PenListener, Printable {
    //////////////////////////////////////////////////////////////
    // GLOBAL SHAPE SETTINGS
    //////////////////////////////////////////////////////////////
    /** Colour of this shape */
    private Color colour;
    /** Alpha of this shape */
    private int alpha = 255;
    /** Style of this shape - (1) LINE or (2) SOLID FILL */
    private int style = STYLE_STROKE;
    /** Line Weight if the style is line */
    private float lineWidth = 1F;
    //////////////////////////////////////////////////////////////
    // GLOBAL SETTINGS
    ////////////////////////////////////////////////////////////// 
    /** Background colour */
    private Color bgColour;
    /** Old colour set when the colours are swapped */
    private Color oldColour;
    /** Swap state - true if the background is currently swapped in */
    private boolean backgroundActive = false;
    /** 'Redraw' on or off **/
    private boolean redraw = true;
    /** Smoothing on or off */
    boolean smoothing;
    /** Boolean used by the timer to determine if there has been canvas activity */
    private boolean canvasChanged = false;
    /** Draw under the other shapes on the canvas */
    private boolean drawUnder = false;
    //////////////////////////////////////////////////////////////
    // PEN SETTINGS
    //////////////////////////////////////////////////////////////
    /** Events on or off - stop mouse/pen events to the modules when inside the UI */
    private boolean events = true;
    private boolean createEvents = true;
    private boolean affectEvents = true;
    /** The Pen manager used by JPen*/
    private PenManager pm;
    /** Pen down or up */
    private boolean penDown = false;
    /** The type of pen - PEN_STYLUS / PEN_ERASER / PEN_CURSOR */
    private int penType = PEN_CURSOR;
    /** Pen Pressure if available */
    private float penPressure = 0F;
    /** Pen Tilt if available */
    private Point2D.Float penTilt = new Point2D.Float();
    /** Pen Location - if a pen is available this will be a float otherwise int */
    private Point2D.Float penLocation = new Point2D.Float();
    /** Pen location has changed or not */
    private boolean penLocationChanged = true;
    //////////////////////////////////////////////////////////////
    // SHAPES
    //////////////////////////////////////////////////////////////
    /** Array list containing shapes that have been archived */
    public ArrayList<AlcShape> shapes;
    /** Array list containing shapes made by create modules */
    public ArrayList<AlcShape> createShapes;
    /** Array list containing shapes made by affect modules */
    public ArrayList<AlcShape> affectShapes;
    /** Array list containing shapes used as visual guides - not actual geometry */
    public ArrayList<AlcShape> guideShapes;
    /** Full shape array of each array list */
    ArrayList[] fullShapeList = new ArrayList[3];
    /** Active shape list plus guides */
    ArrayList[] activeShapeList = new ArrayList[2];
    //////////////////////////////////////////////////////////////
    // IMAGE
    //////////////////////////////////////////////////////////////
    /** An image of the canvas drawn behind active shapes */
    private Image canvasImage;
    /** Image than can be drawn on the canvas */
    private Image image;
    /** Display the Image or not */
    private boolean imageDisplay = false;
    /** Position to display the image */
    private Point imageLocation = new Point(0, 0);
    /** An image used to fake transparency in fullscreen mode */
    private Image transparentImage;
    //////////////////////////////////////////////////////////////
    // DISPLAY
    //////////////////////////////////////////////////////////////
    /** Record indicator on/off */
    private boolean recordIndicator = false;
    /** Draw guides */
    private boolean guides = true;
    /** Graphics Envrionment - updated everytime the volatile buffImage is refreshed */
    private GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    /** Graphics Configuration - updated everytime the volatile buffImage is refreshed */
    private GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
    /** A Vector based canvas for full redrawing */
    private static VectorCanvas vectorCanvas;
    /** Previous cursor */
    Cursor oldCursor;
    /** Automatic toggling of the toolbar */
    private boolean autoToggleToolBar;

    /** Creates a new instance of AlcCanvas*/
    AlcCanvas() {

        this.smoothing = Alchemy.preferences.smoothing;
        this.bgColour = new Color(Alchemy.preferences.bgColour);
        this.colour = new Color(Alchemy.preferences.colour);
        this.autoToggleToolBar = !Alchemy.preferences.paletteAttached;

        this.addMouseListener(this);
        this.addMouseMotionListener(this);

        shapes = new ArrayList<AlcShape>(100);
        shapes.ensureCapacity(100);
        createShapes = new ArrayList<AlcShape>(25);
        createShapes.ensureCapacity(25);
        affectShapes = new ArrayList<AlcShape>(25);
        affectShapes.ensureCapacity(25);
        guideShapes = new ArrayList<AlcShape>(25);
        guideShapes.ensureCapacity(25);

        fullShapeList[0] = shapes;
        fullShapeList[1] = createShapes;
        fullShapeList[2] = affectShapes;

        activeShapeList[0] = createShapes;
        activeShapeList[1] = affectShapes;

        vectorCanvas = new VectorCanvas();

        pm = new PenManager(this);
        pm.pen.addListener(this);

        this.setCursor(CURSOR_CROSS);
    }

    /** Bitmap Canvas
     *  Draws all current shapes on top of the buffered image
     */
    @Override
    public void paintComponent(Graphics g) {

        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        java.awt.Rectangle visibleRect = this.getVisibleRect();
        int w = visibleRect.width;
        int h = visibleRect.height;

        // Draw the 'fake' Transparent Image
        if (Alchemy.window.isTransparent() && transparentImage != null) {
            g2.drawImage(transparentImage, 0, 0, null);

            // Draw the image if present
            if (imageDisplay && image != null) {
                g2.drawImage(image, imageLocation.x, imageLocation.y, null);
            }
        } else {
            // Draw the image if present
            if (imageDisplay && image != null) {
                g2.drawImage(image, imageLocation.x, imageLocation.y, null);
            }
            // Paint background.
            g2.setColor(bgColour);
            g2.fillRect(0, 0, w, h);
        }


        if (smoothing) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        } else {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        // Draw the flattened buffImage
        if (canvasImage != null && !drawUnder) {
            g2.drawImage(canvasImage, 0, 0, null);
        }
        if (redraw) {
            // Draw the create, affect, and guide lists
            for (int j = 0; j < activeShapeList.length; j++) {
                for (int i = 0; i < activeShapeList[j].size(); i++) {
                    AlcShape currentShape = (AlcShape) activeShapeList[j].get(i);
                    // LINE
                    if (currentShape.style == STYLE_STROKE) {
                        //g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL));
                        g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
                        g2.setColor(currentShape.colour);
                        g2.draw(currentShape.path);
                    // SOLID
                    } else {
                        g2.setColor(currentShape.colour);
                        g2.fill(currentShape.path);
                    }
                }
            }
        }

        // Draw the image on top of the current shapes
        if (drawUnder) {
            g2.drawImage(canvasImage, 0, 0, null);
        }

        // Draw a red circle when saving a frame
        if (recordIndicator) {
            Ellipse2D.Double recordCircle = new Ellipse2D.Double(5, h - 12, 7, 7);
            g2.setColor(Color.RED);
            g2.fill(recordCircle);
        }


        // Draw the guides as required
        if (guides) {
            for (int i = 0; i < guideShapes.size(); i++) {
                AlcShape currentShape = guideShapes.get(i);
                // LINE
                if (currentShape.style == STYLE_STROKE) {
                    //g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL));
                    g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
                    g2.setColor(currentShape.colour);
                    g2.draw(currentShape.path);
                // SOLID
                } else {
                    g2.setColor(currentShape.colour);
                    g2.fill(currentShape.path);
                }
            }
        }

        g2.dispose();

    // Hints that don't seem to offer any extra performance on OSX
    //g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
    //g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);

    }

    //////////////////////////////////////////////////////////////
    // CANVAS FUNCTIONALITY
    //////////////////////////////////////////////////////////////
    /** Redraw the canvas */
    public void redraw() {
        redraw(false);
    }

    /** Redraw the canvas
     *  @param fullRedraw   Specify if the full set of vector shapes should be redrawn
     *                  or just add the new shape to the existing buffer image
     */
    public void redraw(boolean fullRedraw) {
        applyAffects();
        if (redraw) {
            if (fullRedraw) {
                canvasImage = renderCanvas(true);
            }
            this.repaint();
            // Something has happened on the canvas and the user is still active
            canvasChanged = true;
        }
    }

    /** Force the canvas to redraw regardless of the current redraw setting */
    public void forceRedraw() {
        this.setRedraw(true);
        this.redraw(true);
        this.setRedraw(false);
    }

    /** Set the canvas redraw state
     * @param redraw    Redraw state 
     */
    public void setRedraw(boolean redraw) {
        this.redraw = redraw;
    }

    /** Get the canvas redraw state
     * @return  Redraw state on or off
     */
    public boolean isRedraw() {
        return redraw;
    }

    /** Set the draw under state to draw under existing shapes 
     * @param drawUnder
     */
    void setDrawUnder(boolean drawUnder) {
        this.drawUnder = drawUnder;
        updateCanvasImage(true);
    }

    /** Get the draw under state
     * @return 
     */
    boolean getDrawUnder() {
        return this.drawUnder;
    }

    /** Update the canvasImage with transparency if required 
     * 
     * @param transparency
     */
    void updateCanvasImage(boolean transparency) {
        canvasImage = renderCanvas(true, true);
    }

    /** Set Smoothing (AntiAliasing) on or off
     * @param smoothing     Smoothing on or off
     */
    void setSmoothing(boolean smoothing) {
        this.smoothing = smoothing;
        if (redraw) {
            this.redraw(true);
        // If redraw is off, just update the canvas image
        } else {
            canvasImage = renderCanvas(true);
        }
    }

    /** Get Antialiasing
     * @return 
     */
    boolean isSmoothing() {
        return this.smoothing;
    }

    /** Return if there has been activity on the canvas since the last time the timer checked */
    boolean canvasChanged() {
        return this.canvasChanged;
    }

    /** Reset the activity flag - called by the timer */
    void resetCanvasChanged() {
        this.canvasChanged = false;
    }
    //////////////////////////////////////////////////////////////
    // PEN EVENTS
    //////////////////////////////////////////////////////////////
    /** Turn on/off Events being sent to modules
     * @param events 
     */
    public void setEvents(boolean events) {
        this.events = events;
    }

    /** Turn on/off Events being sent to create modules
     * @param createEvents 
     */
    public void setCreateEvents(boolean createEvents) {
        this.createEvents = createEvents;
    }

    /** Turn on/off Events being sent to affect modules
     * @param affectEvents 
     */
    public void setAffectEvents(boolean affectEvents) {
        this.affectEvents = affectEvents;
    }

    /** Pen (or mouse) down or up
     * @return  The state of the pen or mouse
     */
    public boolean isPenDown() {
        return penDown;
    }

    /** Pen Pressure if available 
     * @return
     */
    public float getPenPressure() {
        return penPressure;
    }

    /** Pen Tilt  if available 
     * @return  Point2D.Float with tilt information
     */
    public Point2D.Float getPenTilt() {
        return penTilt;
    }

    /** Pen Location as a new Point2D.Float object
     * @return  Point2D.Float with pen location information
     */
    public Point2D.Float getPenLocation() {
        return new Point2D.Float(penLocation.x, penLocation.y);
    }

    /** Set the pen location - set internally by mouse events */
    private void setPenLocation(MouseEvent event) {
        if (penType == PEN_CURSOR) {
            penLocation.x = event.getX();
            penLocation.y = event.getY();
        //System.out.println("Mouse: " + penLocation + " " + penLocationChanged);
        }
    }

    /** Set the pen location - set internally by pen events */
    private void setPenLocation(PLevelEvent ev) {
        for (PLevel level : ev.levels) {
            PLevel.Type levelType = level.getType();
            switch (levelType) {
                case X:
                    penLocation.x = level.value;
                    break;
                case Y:
                    penLocation.y = level.value;
                    break;
            }
        }
    }

    /** Has the pen location changed - useful for filtering out repeats
     * @return Boolean indicating if the pen location has changed or not
     */
    public boolean isPenLocationChanged() {
        return penLocationChanged;
    }

    /** The type of pen being used
     * @return  STYLUS (1) /  ERASER (2) / CURSOR (3) or zero for unknown
     */
    public int getPenType() {
        return penType;
    }

    /** Set the pen type - Default is PEN_CURSOR 
     * @param ev PenEvent from JPen pen tablet library
     */
    private void setPenType() {
        PKind.Type kindType = pm.pen.getKind().getType();
        switch (kindType) {
            case CUSTOM:
                penType = 0;
                break;
            case STYLUS:
                // Set the current pen type
                // Changing the background/foreground setting as required
                if (backgroundActive) {
                    setBackgroundColourActive(false);
                    Alchemy.toolBar.refreshColourButton();
                }
                penType = PEN_STYLUS;
                break;
            case ERASER:
                if (!backgroundActive) {
                    setBackgroundColourActive(true);
                    Alchemy.toolBar.refreshColourButton();
                }
                penType = PEN_ERASER;
                break;
            case CURSOR:
                penType = PEN_CURSOR;
        }
    }

    /** Resize the canvas - called when the window is resized
     * @param windowSize    The new window size
     */
    public void resizeCanvas(Dimension windowSize) {
        // Allow for the left hand toolbar if in 'simple' mode
        int x = 0;
        if (Alchemy.preferences.simpleToolBar) {
            x = Alchemy.toolBar.toolBarWidth;
            windowSize.width -= Alchemy.toolBar.toolBarWidth;
        }
        this.setBounds(x, 0, windowSize.width, windowSize.height);
    }

    /** Clear the canvas */
    public void clear() {
        shapes.clear();
        createShapes.clear();
        affectShapes.clear();
        guideShapes.clear();

        this.canvasImage = null;

        if (redraw) {
            // If a session is loaded then make sure to redraw it below
            if (Alchemy.session.pdfReadPage == null) {
                this.redraw(false);
            } else {
                this.redraw(true);
            }
        // Redraw to clear the screen even if redrawing is off
        } else {
            forceRedraw();
        }
        // Pass this on to the currently selected modules
        Alchemy.plugins.creates[Alchemy.plugins.currentCreate].cleared();

        if (Alchemy.plugins.hasCurrentAffects()) {
            for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                if (Alchemy.plugins.currentAffects[i]) {
                    Alchemy.plugins.affects[i].cleared();
                }
            }
        }
        // Now is a good time to clean up memory
        System.gc();
    }

    /** Set the cursor temporarily - can be restored with {@link #restoreCursor()}
     * @param cursor    New temp cursor
     */
    public void setTempCursor(Cursor cursor) {
        if (oldCursor == null) {
            oldCursor = this.getCursor();
            this.setCursor(cursor);
        }
    }

    /** Restore the cursor */
    public void restoreCursor() {
        if (oldCursor != null) {
            this.setCursor(oldCursor);
            oldCursor = null;
        }
    }

    /** Apply affects to the current shape and redraw the canvas */
    private void applyAffects() {
        if (Alchemy.plugins.hasCurrentAffects()) {
            for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                if (Alchemy.plugins.currentAffects[i]) {
                    Alchemy.plugins.affects[i].affect();
                }
            }
        }
    }

    /** Commit all shapes to the main shapes array */
    public void commitShapes() {
        // Add the createShapes and affectShapes to the main array
        // Add to the bottom if drawUnder is on
        if (drawUnder) {
            shapes.addAll(0, createShapes);
            shapes.addAll(0, affectShapes);
            // Refresh the canvasImage after the shapes have been added
            // to keep the ordering correct
            createShapes.clear();
            affectShapes.clear();
            canvasImage = renderCanvas(true, true);

        // Otherwise add to the top
        } else {
            // If the window is transparent
            if (Alchemy.window.isTransparent()) {
                canvasImage = renderCanvas(true, true);
            } else {
                canvasImage = renderCanvas(false);
            }
            shapes.addAll(createShapes);
            shapes.addAll(affectShapes);
            createShapes.clear();
            affectShapes.clear();
        }

        // Tell the modules the shapes have been commited
        if (Alchemy.plugins.currentCreate >= 0) {
            Alchemy.plugins.creates[Alchemy.plugins.currentCreate].commited();
        }
        if (Alchemy.plugins.hasCurrentAffects()) {
            for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                if (Alchemy.plugins.currentAffects[i]) {
                    Alchemy.plugins.affects[i].commited();
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////
    // SHAPES
    //////////////////////////////////////////////////////////////
    /** Returns the most recently added shape
     * @return The current shape
     */
    public AlcShape getCurrentShape() {
        if (shapes.size() > 0) {
            return shapes.get(shapes.size() - 1);
        } else {
            return null;
        }
    }

    /** Sets the most recently added shape
     * @param shape Shape to become the current shape
     */
    public void setCurrentShape(AlcShape shape) {
        if (shapes.size() > 0) {
            shapes.set(shapes.size() - 1, shape);
        }
    }

    /** Removes the most recently added shape */
    public void removeCurrentShape() {
        if (shapes.size() > 0) {
            shapes.remove(shapes.size() - 1);
        }
    }

    //////////////////////////////////////////////////////////////
    // CREATE SHAPES
    //////////////////////////////////////////////////////////////
    /** Returns the most recently added create shape
     * @return The current create shape
     */
    public AlcShape getCurrentCreateShape() {
        if (createShapes.size() > 0) {
            return createShapes.get(createShapes.size() - 1);
        }
        return null;
    }

    /** Sets the most recently added create shape
     * @param shape     Shape to become the current create shape
     */
    public void setCurrentCreateShape(AlcShape shape) {
        if (createShapes.size() > 0) {
            createShapes.set(createShapes.size() - 1, shape);
        }
    }

    /** Removes the most recently added create shape */
    public void removeCurrentCreateShape() {
        if (createShapes.size() > 0) {
            createShapes.remove(createShapes.size() - 1);
        }
    }

    /** Commit all create shapes to the main shapes array */
    public void commitCreateShapes() {
        canvasImage = renderCanvas(false);
        for (int i = 0; i < createShapes.size(); i++) {
            shapes.add(createShapes.get(i));
        }
        createShapes.clear();
    }

    //////////////////////////////////////////////////////////////
    // AFFECT SHAPES
    //////////////////////////////////////////////////////////////
    /** Returns the most recently added affect shape
     * @return The current create shape
     */
    public AlcShape getCurrentAffectShape() {
        if (affectShapes.size() > 0) {
            return affectShapes.get(affectShapes.size() - 1);
        } else {
            return null;
        }
    }

    /** Sets the most recently added affect shape
     * @param shape     Shape to become the current affect shape
     */
    public void setCurrentAffectShape(AlcShape shape) {
        if (affectShapes.size() > 0) {
            affectShapes.set(affectShapes.size() - 1, shape);
        }
    }

    /** Removes the most recently added affect shape */
    public void removeCurrentAffectShape() {
        if (affectShapes.size() > 0) {
            affectShapes.remove(affectShapes.size() - 1);
        }
    }

    /** Commit all affect shapes to the main shapes array */
    public void commitAffectShapes() {
        canvasImage = renderCanvas(false);

        for (int i = 0; i < affectShapes.size(); i++) {
            shapes.add(affectShapes.get(i));
        }
        affectShapes.clear();
    }

    //////////////////////////////////////////////////////////////
    // GUIDE SHAPES
    //////////////////////////////////////////////////////////////
    /** Returns the most recently added guide shape
     * @return The current guide shape
     */
    public AlcShape getCurrentGuideShape() {
        if (guideShapes.size() > 0) {
            return guideShapes.get(guideShapes.size() - 1);
        } else {
            return null;
        }
    }

    /** Sets the most recently added guide shape
     * @param shape     Shape to become the current guide shape
     */
    public void setCurrentGuideShape(AlcShape shape) {
        if (guideShapes.size() > 0) {
            guideShapes.set(guideShapes.size() - 1, shape);
        }
    }

    /** Removes the most recently added guide shape */
    public void removeCurrentGuideShape() {
        if (guideShapes.size() > 0) {
            guideShapes.remove(guideShapes.size() - 1);
        }
    }

    //////////////////////////////////////////////////////////////
    // SHAPE/COLOUR SETTINGS
    //////////////////////////////////////////////////////////////
    /** Get the current colour
     * @return      The current colour
     */
    public Color getColour() {
        return colour;
    }

    /** Set the current colour
     * @param colour 
     */
    public void setColour(Color colour) {
        // Control how the Foreground/Background button is updated
        if (backgroundActive) {
            bgColour = new Color(colour.getRed(), colour.getGreen(), colour.getBlue());
            this.colour = new Color(colour.getRed(), colour.getGreen(), colour.getBlue());
            redraw(true);
        } else {
            this.colour = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
        }

        if (Alchemy.preferences.paletteAttached || Alchemy.preferences.simpleToolBar) {
            Alchemy.toolBar.refreshColourButton();
//        } else {
//            Alchemy.toolBar.queueColourButtonRefresh();
        }
    }

    /** Get the old colour */
    Color getOldColour() {
        return oldColour;
    }

    /** Set the old colour when backgroundActive state is true */
    void setOldColour(Color colour) {
        this.oldColour = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    /** Whether the background is active or not
     * @return State of the background
     */
    public boolean isBackgroundColourActive() {
        return backgroundActive;
    }

    /** Set the background colour to be active 
     * @param backgroundActive  
     */
    public void setBackgroundColourActive(boolean backgroundActive) {
        // Save the foreground colour and bring the bg colour to the front
        if (backgroundActive) {
            oldColour = colour;
            colour = new Color(bgColour.getRGB());
            this.backgroundActive = true;
        } else {
            // Revert to the old foreground colour
            colour = new Color(oldColour.getRed(), oldColour.getGreen(), oldColour.getBlue(), alpha);
            this.backgroundActive = false;
        }
    }

    /** Get the Background Colour
     * @return 
     */
    public Color getBackgroundColour() {
        if (backgroundActive) {
            return colour;
        } else {
            return bgColour;
        }
    }

    /** Set the Background Colour
     * @param bgColour 
     */
    public void setBackgroundColour(Color bgColour) {
        // Ignore the alpha value
        this.bgColour = new Color(bgColour.getRed(), bgColour.getGreen(), bgColour.getBlue());
        if (backgroundActive) {
            colour = new Color(bgColour.getRed(), bgColour.getGreen(), bgColour.getBlue());
        }
        redraw(true);
    }

    /** Get the current forground colour
     *  This method returns the foreground colour 
     *  even if it is not currently active.
     *  E.g. The active colour is the background colour
     * @return
     */
    public Color getForegroundColour() {
        if (backgroundActive) {
            return oldColour;
        } else {
            return colour;
        }
    }

    /** Set the Foreground Colour
     * @param colour 
     */
    public void setForegroundColour(Color colour) {
        if (backgroundActive) {
            this.oldColour = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
        } else {
            this.colour = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
        }
    }

    /** Get the current alpha value
     * @return 
     */
    public int getAlpha() {
        return alpha;
    }

    /** Set the current alpha value
     * @param alpha 
     */
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        if (backgroundActive) {
            setOldColour(this.oldColour);
        } else {
            setColour(this.colour);
        }
        Alchemy.toolBar.refreshTransparencySlider();
    }

    /** Get the current style
     * @return 
     */
    public int getStyle() {
        return style;
    }

    /** Set the current style
     * @param style 
     */
    public void setStyle(int style) {
        this.style = style;
    }

    /** Toggle the style between line and solid */
    public void toggleStyle() {
        if (style == STYLE_STROKE) {
            style = STYLE_FILL;
        } else {
            style = STYLE_STROKE;
        }
    }

    /** Get the current line width
     * @return 
     */
    public float getLineWidth() {
        return lineWidth;
    }

    /** Set the current line width
     * @param lineWidth 
     */
    public void setLineWidth(float lineWidth) {
        this.lineWidth = lineWidth;
    }
    //////////////////////////////////////////////////////////////
    // DISPLAY
    //////////////////////////////////////////////////////////////
    boolean isGuideEnabled() {
        return guides;
    }

    void setGuide(boolean guides) {
        this.guides = guides;
    }

    /** Returns if the record indicator (used with session auto-saving) is enabled 
     * @return
     */
    public boolean isRecordIndicatorEnabled() {
        return recordIndicator;
    }

    /** Set the display of the record indicator (used with session auto-saving)
     * @param recordIndicator   On or off
     */
    public void setRecordIndicator(boolean recordIndicator) {
        this.recordIndicator = recordIndicator;
    }

    /** Manage the automatic toggling of the toolbar */
    void setAutoToggleToolBar(boolean manageToolBar) {
        if (!Alchemy.preferences.paletteAttached) {
            this.autoToggleToolBar = manageToolBar;
        }
    }

    /** If the toolbar is being automatically toggled on/off or not */
    boolean isAutoToggleToolBar() {
        if (Alchemy.preferences.paletteAttached) {
            return false;
        } else {
            return this.autoToggleToolBar;
        }
    }
    //////////////////////////////////////////////////////////////
    // IMAGE
    //////////////////////////////////////////////////////////////
    /** Set the Image to be drawn on the canvas
     * 
     * @param image Image to be drawn
     */
    public void setImage(BufferedImage image) {
        this.image = image;
        canvasImage = renderCanvas(true);
    }

    /** Get the current image
     * 
     * @return  The current image
     */
    public Image getImage() {
        return this.image;
    }

    /** Check if an Image is defined or not
     * 
     * @return Image display on or off
     */
    public boolean isImageSet() {
        return image == null ? false : true;
    }

    /** Set image display to on or off
     * 
     * @param imageDisplay Image display on or off
     */
    public void setImageDisplay(boolean imageDisplay) {
        this.imageDisplay = imageDisplay;
        canvasImage = renderCanvas(true);
    }

    /** Check if image display is enabled
     * @return 
     */
    public boolean isImageDisplayEnabled() {
        return imageDisplay;
    }

    /** Set the location for the image to be displayed on the canvas
     * 
     * @param p
     */
    public void setImageLocation(Point p) {
        this.imageLocation = p;
    }

    /** Set the location for the image to be displayed on the canvas
     * 
     * @param x
     * @param y
     */
    public void setImageLocation(int x, int y) {
        this.imageLocation.x = x;
        this.imageLocation.y = y;
    }

    /** Get the location where the image is displayed on the canvas
     * 
     * @return  Point - x & y location
     */
    public Point getImageLocation() {
        return imageLocation;
    }

    /** Reset the image location back to zero */
    public void resetImageLocation() {
        this.imageLocation.x = 0;
        this.imageLocation.y = 0;
    }

    /** Set the transparent image to be drawn behind the canvas
     * 
     * @param transparentImage
     */
    void setTransparentImage(Image transparentImage) {
        this.transparentImage = transparentImage;
    }

    /** Create an image from the canvas
     * 
     * @param vectorMode    In vector mode all shapes are rendered from scratch.
     *                      Otherwise the active shapes are rendered on top of the current canvas image
     * @return
     */
    Image renderCanvas(boolean vectorMode) {
        return renderCanvas(vectorMode, false, 1);
    }

    /** Create an image from the canvas
     * 
     * @param vectorMode    In vector mode all shapes are rendered from scratch.
     *                      Otherwise the active shapes are rendered on top of the current canvas image
     * @param transparent   Ignore the background and create a transparent image with only shapes
     * @return
     */
    Image renderCanvas(boolean vectorMode, boolean transparent) {
        return renderCanvas(vectorMode, transparent, 1);
    }

    /** Create an image from the canvas
     * 
     * @param vectorMode    In vector mode all shapes are rendered from scratch.
     *                      Otherwise the active shapes are rendered on top of the current canvas image
     * @param scale         Scale setting to scale the canvas up or down
     * @return
     */
    Image renderCanvas(boolean vectorMode, double scale) {
        return renderCanvas(vectorMode, false, scale);
    }

    /** Create an image from the canvas
     * 
     * @param vectorMode    In vector mode all shapes are rendered from scratch.
     *                      Otherwise the active shapes are rendered on top of the current canvas image
     * @param transparent   Ignore the background and create a transparent image with only shapes
     * @param scale         Scale setting to scale the canvas up or down
     * @return
     */
    Image renderCanvas(boolean vectorMode, boolean transparent, double scale) {
        // Get the canvas size with out the frame/decorations
        java.awt.Rectangle visibleRect = this.getVisibleRect();
        ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
        BufferedImage buffImage;
        if (transparent) {
            buffImage = gc.createCompatibleImage(visibleRect.width, visibleRect.height, Transparency.TRANSLUCENT);
        } else {
            buffImage = gc.createCompatibleImage(visibleRect.width, visibleRect.height);
        }
        // Paint the buffImage with the canvas
        Graphics2D g2 = buffImage.createGraphics();
        // Make sure the record indicator is off
        recordIndicator = false;

        if (scale != 1) {
            g2.scale(scale, scale);
        }

        if (transparent) {
            vectorCanvas.transparent = true;
            vectorCanvas.paintComponent(g2);
            vectorCanvas.transparent = false;
        } else {
            if (vectorMode) {
                vectorCanvas.paintComponent(g2);
            } else {
                this.paintComponent(g2);
            }
        }
        g2.dispose();
        return buffImage;
    }
    //////////////////////////////////////////////////////////////
    // SAVE BITMAP
    //////////////////////////////////////////////////////////////
    /** Save the canvas to a bitmap file
     * 
     * @param file  The file object to save the bitmap to
     * @return      True if save worked, otherwise false
     */
    boolean saveBitmap(File file) {
        return saveBitmap(file, "png", false);
    }

    /** Save the canvas to a bitmap file
     * 
     * @param file          The file object to save the bitmap to
     * @param transparent   An image with transparency or not
     * @return              True if save worked, otherwise false
     */
    boolean saveBitmap(File file, boolean transparent) {
        return saveBitmap(file, "png", transparent);
    }

    /** Save the canvas to a bitmap file
     * 
     * @param file          The file object to save the bitmap to
     * @param format        The file format to save in
     * @return              True if save worked, otherwise false
     */
    boolean saveBitmap(File file, String format) {
        return saveBitmap(file, format, false);
    }

    // TODO - Scaleable image export
    /** Save the canvas to a bitmap file
     * 
     * @param file          The file object to save the bitmap to
     * @param format        The file format to save in
     * @param transparent   An image with transparency or not
     * @return              True if save worked, otherwise false
     */
    boolean saveBitmap(File file, String format, boolean transparent) {
        try {
            setGuide(false);
            Image bitmapImage;
            if (transparent) {
                bitmapImage = renderCanvas(true, true);
            } else {
                bitmapImage = renderCanvas(true);
            }
            setGuide(true);
            ImageIO.write((BufferedImage) bitmapImage, format, file);
            return true;
        } catch (IOException ex) {
            System.err.println(ex);
            return false;
        }
    }

    //////////////////////////////////////////////////////////////
    // PDF STUFF
    //////////////////////////////////////////////////////////////
    /** Save the canvas to a single paged PDF file
     * 
     * @param file  The file object to save the pdf to
     * @return      True if save worked, otherwise false
     */
    boolean saveSinglePdf(File file) {
        // Get the current 'real' size of the canvas without margins/borders
        java.awt.Rectangle visibleRect = this.getVisibleRect();
        //int singlePdfWidth = Alchemy.window.getWindowSize().width;
        //int singlePdfHeight = Alchemy.window.getWindowSize().height;
        Document singleDocument = new Document(new com.lowagie.text.Rectangle(visibleRect.width, visibleRect.height), 0, 0, 0, 0);
        System.out.println("Save Single Pdf Called: " + file.toString());

        try {

            PdfWriter singleWriter = PdfWriter.getInstance(singleDocument, new FileOutputStream(file));
            singleDocument.addTitle("Alchemy Session");
            singleDocument.addAuthor(USER_NAME);
            singleDocument.addCreator("Alchemy <http://al.chemy.org>");

            // Add metadata and open the document
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            XmpWriter xmp = new XmpWriter(os);
            PdfSchema pdf = new PdfSchema();
            pdf.setProperty(PdfSchema.KEYWORDS, "Alchemy <http://al.chemy.org>");
            pdf.setProperty(PdfSchema.VERSION, "1.4");
            xmp.addRdfDescription(pdf);
            xmp.close();
            singleWriter.setXmpMetadata(os.toByteArray());

            singleDocument.open();
            PdfContentByte singleContent = singleWriter.getDirectContent();
            singleContent.setDefaultColorspace(PdfName.CS, PdfName.DEVICERGB);

            Graphics2D g2pdf = singleContent.createGraphics(visibleRect.width, visibleRect.height);

            setGuide(false);
            vectorCanvas.paintComponent(g2pdf);
            setGuide(true);

            g2pdf.dispose();

            singleDocument.close();
            return true;

        } catch (DocumentException ex) {
            System.err.println(ex);
            return false;
        } catch (IOException ex) {
            System.err.println(ex);
            return false;
        }
    }

    /** Adds a pdfReadPage to an existing pdf file
     * 
     * @param mainPdf   The main pdf with multiple pages.
     *                  Also used as the destination file.
     * @param tempPdf   The 'new' pdf with one pdfReadPage to be added to the main pdf
     * @return
     */
    boolean addPageToPdf(File mainPdf, File tempPdf) {
        try {
            // Destination file created in the temp dir then we will move it
            File dest = new File(DIR_TEMP, "Alchemy.pdf");
            OutputStream output = new FileOutputStream(dest);

            PdfReader reader = new PdfReader(mainPdf.getPath());
            PdfReader newPdf = new PdfReader(tempPdf.getPath());

            // See if the size of the canvas has increased
            // Size of the most recent temp PDF
            com.lowagie.text.Rectangle currentSize = newPdf.getPageSizeWithRotation(1);
            // Size of the session pdf at present
            com.lowagie.text.Rectangle oldSize = reader.getPageSizeWithRotation(1);
            // Sizes to be used from now on
            float pdfWidth = oldSize.getWidth();
            float pdfHeight = oldSize.getHeight();
            float shrinkOffset = 0;
            if (currentSize.getWidth() > pdfWidth) {
                pdfWidth = currentSize.getWidth();
            }
            if (currentSize.getHeight() > pdfHeight) {
                pdfHeight = currentSize.getHeight();
            }
            // Create an offset if the canvas has shrunk down
            if (currentSize.getHeight() < pdfHeight) {
                shrinkOffset = 0 - (currentSize.getHeight() - pdfHeight);
            }
            // Use the new bigger canvas size if required
            Document mainDocument = new Document(new com.lowagie.text.Rectangle(pdfWidth, pdfHeight), 0, 0, 0, 0);
            PdfWriter mainWriter = PdfWriter.getInstance(mainDocument, output);

            // Copy the meta data
            mainDocument.addTitle("Alchemy Session");
            mainDocument.addAuthor(USER_NAME);
            mainDocument.addCreator("Alchemy <http://al.chemy.org>");
            mainWriter.setXmpMetadata(reader.getMetadata());
            mainDocument.open();

            // Holds the PDF
            PdfContentByte mainContent = mainWriter.getDirectContent();
            // Set the colour space to RGB
            // & hopefully avoid the pdf reader switching to CMYK
            mainContent.setDefaultColorspace(PdfName.CS, PdfName.DEVICERGB);

            // Add each page from the main PDF
            for (int i = 0; i < reader.getNumberOfPages();) {
                ++i;
                mainDocument.newPage();
                PdfImportedPage page = mainWriter.getImportedPage(reader, i);

                float yOffset = 0;
                float pageHeight = page.getHeight();
                // Because the origin is bottom left
                // Create an offset to align fromt he top left
                // This will only happen when a newer bigger page is added
                if (pageHeight < pdfHeight) {
                    yOffset = pdfHeight - pageHeight;
                }
                mainContent.addTemplate(page, 0, yOffset);
            }
            // Add the last (new) page
            mainDocument.newPage();
            PdfImportedPage lastPage = mainWriter.getImportedPage(newPdf, 1);
            // If the page has been shrunk down again
            // move the new page back up into the top left
            mainContent.addTemplate(lastPage, 0, shrinkOffset);
            output.flush();
            mainDocument.close();
            output.close();

            if (dest.exists()) {
                // Save the location of the main pdf
                String mainPdfPath = mainPdf.getPath();
                // Delete the old file
                if (mainPdf.exists()) {
                    mainPdf.delete();
                }
                // The final joined up pdf file
                File joinPdf = new File(mainPdfPath);
                // Rename the file
                boolean success = dest.renameTo(joinPdf);
                if (!success) {
                    System.err.println("Error moving Pdf");
                    return false;
                }

            } else {
                System.err.println("File does not exist?!: " + dest.getAbsolutePath());
                return false;
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    //////////////////////////////////////////////////////////////
    // PRINT STUFF
    //////////////////////////////////////////////////////////////
    /**
     * This is the method defined by the Printable interface.  It prints the
     * canvas to the specified Graphics object, respecting the paper size
     * and margins specified by the PageFormat.  If the specified pdfReadPage number
     * is not pdfReadPage 0, it returns a code saying that printing is complete.  The
     * method must be prepared to be called multiple times per printing request
     * 
     * This code is from the book Java Examples in a Nutshell, 2nd Edition. Copyright (c) 2000 David Flanagan. 
     * 
     *
     * @param g
     * @param format 
     */
    public int print(Graphics g, PageFormat format, int pageIndex) throws PrinterException {
        // We are only one pdfReadPage long; reject any other pdfReadPage numbers
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        // The Java 1.2 printing API passes us a Graphics object, but we
        // can always cast it to a Graphics2D object
        Graphics2D g2p = (Graphics2D) g;

        // Translate to accomodate the requested top and left margins.
        g2p.translate(format.getImageableX(), format.getImageableY());


        // Figure out how big the drawing is, and how big the pdfReadPage (excluding margins) is
        Dimension size = this.getSize();                  // Canvas size
        double pageWidth = format.getImageableWidth();    // Page width
        double pageHeight = format.getImageableHeight();  // Page height

        // If the canvas is too wide or tall for the pdfReadPage, scale it down
        if (size.width > pageWidth) {
            double factor = pageWidth / size.width;  // How much to scale
            System.out.println("Width Scale: " + factor);
            g2p.scale(factor, factor);              // Adjust coordinate system
            pageWidth /= factor;                   // Adjust pdfReadPage size up
            pageHeight /= factor;
        }

        if (size.height > pageHeight) {   // Do the same thing for height
            double factor = pageHeight / size.height;
            System.out.println("Height Scale: " + factor);
            g2p.scale(factor, factor);
            pageWidth /= factor;
            pageHeight /= factor;

        }

        // Now we know the canvas will fit on the pdfReadPage.  Center it by translating as necessary.
        g2p.translate((pageWidth - size.width) / 2, (pageHeight - size.height) / 2);

        // Draw a line around the outside of the drawing area
        //g2.drawRect(-1, -1, size.width + 2, size.height + 2);

        // Set a clipping region so the canvas doesn't go out of bounds
        g2p.setClip(0, 0, size.width, size.height);

        // Finally, print the component by calling the paintComponent() method.
        // Or, call paint() to paint the component, its background, border, and
        // children, including the Print JButton
        vectorCanvas.paintComponent(g);

        // Tell the PrinterJob that the pdfReadPage number was valid
        return Printable.PAGE_EXISTS;

    }

    //////////////////////////////////////////////////////////////
    // MOUSE EVENTS
    //////////////////////////////////////////////////////////////
    public void mouseMoved(MouseEvent event) {
        setPenLocation(event);
        if (isAutoToggleToolBar()) {
            Alchemy.toolBar.toggleToolBar(event.getY());
        }
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseMoved(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseMoved(event);
                        }
                    }
                }
            }
        }
        if (penType != PEN_CURSOR) {
            penLocationChanged = false;
        }
    }

    public void mousePressed(MouseEvent event) {
        penDown = true;
        // Hide the toolbar when clicking on the canvas
        if (!Alchemy.preferences.paletteAttached && Alchemy.toolBar.isToolBarVisible() &&
                !Alchemy.preferences.simpleToolBar && event.getY() >= Alchemy.toolBar.getTotalHeight()) {
            Alchemy.toolBar.setToolBarVisible(false);
        }

        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mousePressed(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mousePressed(event);
                        }
                    }
                }
            }
        }
    }

    public void mouseClicked(MouseEvent event) {
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseClicked(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseClicked(event);
                        }
                    }
                }
            }
        }
    }

    public void mouseEntered(MouseEvent event) {
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseEntered(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseEntered(event);
                        }
                    }
                }
            }
        }
    }

    public void mouseExited(MouseEvent event) {
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseExited(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseExited(event);
                        }
                    }
                }
            }
        }
    }

    public void mouseReleased(MouseEvent event) {
        penDown = false;
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseReleased(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseReleased(event);
                        }
                    }
                }
            }
        }
    }

    public void mouseDragged(MouseEvent event) {
        setPenLocation(event);
        if (events) {
            // Pass to the current create module
            if (createEvents) {
                Alchemy.plugins.creates[Alchemy.plugins.currentCreate].mouseDragged(event);
            }
            // Pass to all active affect modules
            if (affectEvents) {
                if (Alchemy.plugins.hasCurrentAffects()) {
                    for (int i = 0; i < Alchemy.plugins.currentAffects.length; i++) {
                        if (Alchemy.plugins.currentAffects[i]) {
                            Alchemy.plugins.affects[i].mouseDragged(event);
                        }
                    }
                }
            }
        }
        if (penType != PEN_CURSOR) {
            penLocationChanged = false;
        }
    }
    //////////////////////////////////////////////////////////////
    // PEN EVENTS
    //////////////////////////////////////////////////////////////
    public void penKindEvent(PKindEvent ev) {
        setPenType();
    }

    public void penLevelEvent(PLevelEvent ev) {
        //setPenType();
        // Register the pen pressure, tilt and location 
        // Do this only if this is an actual pen
        // Otherwise register pen location using the mouse
        if (penType != PEN_CURSOR) {
            // Pressure and tilt is only good when the pen is down
            if (penDown) {
                penPressure = pm.pen.getLevelValue(PLevel.Type.PRESSURE);
                // parabolic sensitivity
                penPressure *= penPressure;
                penTilt.x = pm.pen.getLevelValue(PLevel.Type.TILT_X);
                penTilt.y = pm.pen.getLevelValue(PLevel.Type.TILT_Y);
            }
            // If this event is a movement
            if (ev.isMovement()) {
                // Register the pen location even when the mouse is up
                setPenLocation(ev);
                penLocationChanged = true;
            }
        }
    }

    public void penButtonEvent(PButtonEvent arg0) {
    }

    public void penScrollEvent(PScrollEvent arg0) {
    }

    public void penTock(long arg0) {
    }

    /** Vector Canvas
     *  Draws the canvas is full, including all shapes,
     *  the background and buffImage if any.
     */
    class VectorCanvas extends JPanel implements AlcConstants {

        boolean transparent = false;

        @Override
        public void paintComponent(Graphics g) {

            super.paintComponent(g);

            int w = Alchemy.canvas.getWidth();
            int h = Alchemy.canvas.getHeight();

            Graphics2D g2 = (Graphics2D) g;

            if (Alchemy.canvas.smoothing) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            } else {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            }

            // Do not draw the background when creating a transparent image
            if (!transparent) {
                // Paint background.
                g2.setColor(Alchemy.canvas.getBackgroundColour());
                g2.fillRect(0, 0, w, h);
            }

            // PDF READER
            if (Alchemy.session.pdfReadPage != null) {

                // Remember the old transform settings
                AffineTransform at = g2.getTransform();

                int pageWidth = (int) Alchemy.session.pdfReadPage.getWidth();
                int pageHeight = (int) Alchemy.session.pdfReadPage.getHeight();
                PDFRenderer renderer = new PDFRenderer(Alchemy.session.pdfReadPage, g2, new Rectangle(0, 0, pageWidth, pageHeight), null, Alchemy.canvas.getBackgroundColour());
                try {
                    Alchemy.session.pdfReadPage.waitForFinish();
                    renderer.run();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                // Revert to the old transform settings
                g2.setTransform(at);

            }

            // Draw Image
            if (Alchemy.canvas.isImageDisplayEnabled() && Alchemy.canvas.isImageSet()) {
                Point p = Alchemy.canvas.getImageLocation();
                g2.drawImage(Alchemy.canvas.getImage(), p.x, p.y, null);
            }


            // Draw the shapes, create, and affect lists
            for (int j = 0; j < Alchemy.canvas.fullShapeList.length; j++) {
                for (int i = 0; i < Alchemy.canvas.fullShapeList[j].size(); i++) {
                    AlcShape currentShape = (AlcShape) Alchemy.canvas.fullShapeList[j].get(i);
                    // LINE
                    if (currentShape.style == STYLE_STROKE) {
                        //g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL));
                        g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
                        g2.setColor(currentShape.colour);
                        g2.draw(currentShape.path);
                    // SOLID
                    } else {
                        g2.setColor(currentShape.colour);
                        g2.fill(currentShape.path);
                    }
                }
            }
            if (Alchemy.canvas.isGuideEnabled()) {
                for (int i = 0; i < Alchemy.canvas.guideShapes.size(); i++) {
                    AlcShape currentShape = Alchemy.canvas.guideShapes.get(i);
                    // LINE
                    if (currentShape.style == STYLE_STROKE) {
                        //g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_BEVEL));
                        g2.setStroke(new BasicStroke(currentShape.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
                        g2.setColor(currentShape.colour);
                        g2.draw(currentShape.path);
                    // SOLID
                    } else {
                        g2.setColor(currentShape.colour);
                        g2.fill(currentShape.path);
                    }
                }
            }

            g2.dispose();
        }
    }
}
