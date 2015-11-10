// Poor man's service implementation for municipality maps components
// (container, server, layers, map). The service encapsulates ajax
// calls and provides component-specific parameters and communication
// mechanism (channels).
//
// From the view-model point of view, there are four important concepts/observables:
// serverDetails: the map server url and the (optional) authentication credentials.
// capabilities: Map server capabilities (result of GetCapabilities WMS request).
// serverLayers: List of selectable (named) layers of the current server.
// userLayers: User selected/defined organization layers. Each organization's layers
// are later combined in the backend into one set of municipality layers.

LUPAPISTE.MunicipalityMapsService = function() {
  "use strict";
  var self = this;

  var PROXY = "/proxy/organization-map-server";

  function Layer( opts) {
    opts = _.defaults(opts || {}, {name: "", id: "", preview: true,
                                   fixed: false});
    // The user defined (with the exception of asemakaava and
    // kantakartta fixed layers) name of the layer.
    this.name = ko.observable( opts.name );
    // Layer's name in the capabilities
    this.id = ko.observable( opts.id );
    // Is this layer visible in the preview map?
    this.preview = ko.observable( opts.preview );
    // Fixed layers cannot be renamed or removed by the user.
    // Currently there are two fixed layers: asemakaava and
    // kantakartta. However, the user can leave the undefined by not
    // selecting a layer for them.
    this.fixed = opts.fixed;
  }

  var serverDetails = ko.observable();
  var userLayers = ko.observableArray();

  var error = ko.observable();
  var waiting = ko.observable( false );
  var capabilities = ko.observable();
  var mapFitted = ko.observable( false );

  var saveLayersFlag = false;

  // Combines (only) the named layers into an array.
  function namedLayers( layer, arr ) {
    arr = arr || [];
    if( layer && layer.Name ) {
      arr.push( {name: layer.Name,
                 title: layer.Title,
                 capabilities: layer});
    }
    if( layer.Layer ) {
      _.each( _.flatten( [layer.Layer]),
              function( item ) {
                namedLayers( item, arr );
              });
    }
    return arr;
  }

  // Whenever the server details change,
  // the map server capabilities are reloaded.
  ko.computed( function() {
    if( serverDetails()) {
      $.get( PROXY,
             {request: "GetCapabilities",
              service: "wms"},
             function( data ) {
               error( false );
               var parser = new ol.format.WMSCapabilities();
               capabilities( parser.read( data ));
             },
             "text") // Text works both for xml and text responses.
      .fail( function() {
        error( true );
      });
    }
  });

  // List of named layers for the current map server.
  // The layers are parsed from the capabilities.
  var serverLayers = ko.pureComputed ( {
    read: function () {
      var caps = capabilities();
      if( caps ) {
        return namedLayers (caps.Capability.Layer);
      }
    },
    deferEvaluation: true
  });

  function resetUserLayers() {
    userLayers.removeAll();
    userLayers( [new Layer( {name: "asemakaava",
                             fixed: true}),
                 new Layer( {name: "kantakartta",
                             fixed: true})]);
  }

  // Reads server details and user layers from
  // the backend. Only called during the initialization.
  function storedSettings() {
    ajax.query( "get-map-layers-data" )
    .pending( waiting )
    .success( function( res ) {
      error( false );
      var server = res.server || {};
      serverDetails( {
        url:  server.url || "",
        username: server.username || "",
        password: server.password || ""
      });
      if( _.size(res.layers) >= 2 ) {
        var layers = _.map( res.layers, function( layer, i ) {
          return new Layer({name: layer.name,
                            id: layer.id,
                            fixed: i < 2});
        });
        mapFitted( false );
        userLayers( layers);
        saveLayersFlag = true;
      }
    })
    .error( function() {
      error( true );
      resetUserLayers();
      saveLayersFlag = true;
    })
    .call();
    return {server: serverDetails,
            layers: userLayers};
  }

  // With the exception of preview property, any
  // change in userLayers results in saving the
  // layers to backend.
  // The userLayers observable is partly modified
  // by the municipality-maps-layers component.
  ko.computed( function () {
    if( _.size( userLayers()) && saveLayersFlag ) {
      ajax.command( "update-user-layers",
                    {layers: _.map( userLayers(),
                                    function( layer ) {
                                      return {
                                        name: layer.name(),
                                        id: layer.id()
                                      };
                                    })})
      .complete( function( res ) {
        util.showSavedIndicator( res.responseJSON );
      } )
      .call();
    }
  });

  // Save server details to backend.
  // The details are set by municipality-maps-server
  // component.
  function updateServerDetails( details ) {
    ajax.command( "update-map-server-details",
                  details)
    .pending( waiting )
    .complete( function( res ) {
      var body = res.responseJSON;
      error( body.ok );
      serverDetails( details );
      util.showSavedIndicator(body);
    })
    .call();
  }

  // Channel is a very simple abstraction on top
  // of the hub.
  var hubId = _.uniqueId( "municipality-maps-");

  function channel( sender ) {
    return {
      send: function( msg ) {
        hub.send( hubId, {
          sender: sender,
          message: msg} );
        }
    };
  }

  // Parameter providers
  self.getParameters = function() {
    var ss = storedSettings();
    return {
      server: {
        server: ss.server,
        waiting: waiting,
        error: error,
        channel: channel( "server")
      },
      layers: {
        Layer: Layer,
        userLayers: ss.layers,
        serverLayers: serverLayers,
        channel: channel( "layers")
      },
      map: {
        PROXY: PROXY,
        capabilities: capabilities,
        serverLayers: serverLayers,
        userLayers: ss.layers,
        mapFitted: mapFitted
      }
    };
  };

  var messageFuns = {
    // Server configuration has been changed by the server component.
    server: function( m ) {
      if( !_.isEqual( m, serverDetails())) {
        error( false );
        capabilities( null );
        resetUserLayers();
        mapFitted( false );
        updateServerDetails( m );
      }
    },
    // A layer has been added or removed by the layers component.
    // Note: other userLayers changes (selection, preview toggle)
    // are handled by observable mechanisms (see computed above and
    // the map component).
    layers: function( m ) {
      if( m.op === "add") {
        userLayers.push( new Layer() );
      }
      if( m.op === "remove" ) {
        userLayers.remove( m.layer );
      }
    }
  };


  hub.subscribe( hubId, function( data ) {
    messageFuns[data.sender]( data.message);
  });
};
