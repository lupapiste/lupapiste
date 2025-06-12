/* Copyright (c) 2006-2013 by OpenLayers Contributors (see authors.txt for
 * full list of contributors). Published under the 2-clause BSD license.
 * See license.txt in the OpenLayers distribution or repository for the
 * full text of the license. */

/**
 * @requires OpenLayers/Control/Panel.js
 * @requires OpenLayers/Control/Navigation.js
 * @requires OpenLayers/Control/DrawFeature.js
 * @requires OpenLayers/Handler/Point.js
 * @requires OpenLayers/Handler/Path.js
 * @requires OpenLayers/Handler/Polygon.js
 */

/**
 * Class: OpenLayers.Control.LupapisteEditingToolbar
 * The EditingToolbar is a panel of 4 controls to draw circles, polygons,
 * points, or to navigate the map by panning. By default it appears in the
 * upper right corner of the map.
 *
 * Inherits from:
 *  - <OpenLayers.Control.Panel>
 */
OpenLayers.Control.LupapisteEditingToolbar = OpenLayers.Class(
  OpenLayers.Control.Panel, {

    /**
     * APIProperty: citeCompliant
     * {Boolean} If set to true, coordinates of features drawn in a map extent
     * crossing the date line won't exceed the world bounds. Default is false.
     */
    citeCompliant: false,

    /**
     * Constructor: OpenLayers.Control.EditingToolbar
     * Create an editing toolbar for a given layer.
     *
     * Parameters:
     * layer - {<OpenLayers.Layer.Vector>}
     * options - {Object}
     */
    initialize: function(layer, options) {
        "use strict";
        OpenLayers.Control.Panel.prototype.initialize.apply(this, [options]);

        // Lupapiste extension: send event to hub
        function sendDrawEvent(featureType, feature) {
          var params = {mapId: util.getIn(layer, ["map", "div", "id"], "unknown"),
                        featureType: featureType, wkt: feature.geometry.toString()};
          if (featureType === "circle") {
            var bounds = feature.geometry.getBounds();
            params.x = (bounds.left + bounds.right) / 2;
            params.y = (bounds.bottom + bounds.top) / 2;
            params.wkt = "POINT(" + params.x + " " + params.y + ")";
            params.radius = (bounds.right - bounds.left) / 2;
          } else if (featureType === "point") {
            params.x = feature.geometry.x;
            params.y = feature.geometry.y;
          } else if (featureType === "polygon") {
            var points = feature.geometry.components[0].components;
            params.points = _.map(points, function(p) {return _.pick(p, ["x", "y"]);});
          }
          hub.send("LupapisteEditingToolbar::featureAdded", params);
        }

        this.addControls(
          [ new OpenLayers.Control.Navigation() ]
        );
        var controls = [
            new OpenLayers.Control.DrawFeature(layer, OpenLayers.Handler.Point, {
                displayClass: "olControlDrawFeaturePoint",
                featureAdded: _.partial(sendDrawEvent, "point"),
                handlerOptions: {citeCompliant: this.citeCompliant}
            }),
            new OpenLayers.Control.DrawFeature(layer, OpenLayers.Handler.Polygon, {
                displayClass: "olControlDrawFeaturePolygon",
                featureAdded: _.partial(sendDrawEvent, "polygon"),
                handlerOptions: {citeCompliant: this.citeCompliant}
            }),
            new OpenLayers.Control.DrawFeature(layer, OpenLayers.Handler.RegularPolygon, {
              displayClass: "olControlDrawFeatureCircle",
              featureAdded: _.partial(sendDrawEvent, "circle"),
              handlerOptions: {citeCompliant: this.citeCompliant, sides: 40}
          })
        ];
        this.addControls(controls);
    },

    /**
     * Method: draw
     * calls the default draw, and then activates mouse defaults.
     *
     * Returns:
     * {DOMElement}
     */
    draw: function() {
        "use strict";
        var div = OpenLayers.Control.Panel.prototype.draw.apply(this, arguments);
        if (this.defaultControl === null) {
            this.defaultControl = this.controls[0];
        }
        return div;
    },

    CLASS_NAME: "OpenLayers.Control.LupapisteEditingToolbar"
});
