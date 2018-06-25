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
  var backgroundMapVisible = ko.observable( true );

  var saveLayersFlag = false;

  // L10n term for the current error status.
  var errorMessageTerm = ko.pureComputed (function() {
    var err = error();
    return err ? "auth-admin.municipality-maps." + err : "empty";
  });

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

  // Parses given WMS capabilities.
  // Throws on error or bad data.
  function parseCapabilities( data ) {
    var parser = new ol.format.WMSCapabilities();
    var caps = parser.read( data );
    if( !_.get( caps, "Capability.Layer" )) {
      throw "bad-data";
    }
    return caps;
  }

  // Whenever the server details change,
  // the map server capabilities are reloaded.
  ko.computed( function() {
    var server = serverDetails();
    if( server && server.url ) {
      error( false );
      waiting( true );
      ajax.get(PROXY).param("request", "GetCapabilities").param("service", "WMS")
        .success(function( data ) {
           error( false );
           var parsedCaps = null;
           try {
             parsedCaps = parseCapabilities( data );
           } catch( e ) {
             // Received data was not capabilities.
             error( "bad-data" );
             window.error("Unable to parse municipality map capabilities: " + e);
           } finally {
             capabilities( parsedCaps );
           }
         })
        .fail(function() {
          error( "error" );
        })
        .complete(_.partial(waiting, false))
        .dataType("text")  // Text works both for xml and text responses.
        .call();
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

  function userLayerArray( layerNames ) {
    return  _.map(layerNames, function(name) {
      return new Layer({name: name,
                        fixed: true});
    });
  }

  function resetUserLayers() {
    userLayers.removeAll();
    userLayers( userLayerArray( ["asemakaava", "kantakartta"] ));
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
      if( _.size(res.layers) >= 1 ) {
        var defaultNames = ["asemakaava", "kantakartta"];
        var layers = _.map( res.layers, function( layer ) {
          _.pull(defaultNames, layer.name);
          return new Layer({name: layer.name,
                            id: layer.id,
                            fixed: layer.base });
        });
        mapFitted( false );
        userLayers( _.concat(userLayerArray(defaultNames), layers));
      } else {
        resetUserLayers();
      }
    })
    .error( function() {
      error( "error" );
      resetUserLayers();
    })
    .complete( function() {
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
    if( _.size( userLayers())) {
      // Calculated always to make sure that
      // the computed binds to every layer.
      var layers = _.map( userLayers(),
                                    function( layer ) {
                                      return {
                                        name: layer.name(),
                                        id: layer.id(),
                                        base: Boolean( layer.fixed)
                                      };
                                    });
      if( saveLayersFlag ) {
        ajax.command ( "update-user-layers", {layers: layers })
        .complete ( function( res ) {
          util.showSavedIndicator( res.responseJSON );
        } )
        .call ();
      }
    }
  });

  // Save server details to backend.
  // The details are set by municipality-maps-server
  // component.
  function updateServerDetails( details ) {
    ajax.command("update-map-server-details", details)
    .pending(waiting)
    .onError("error.invalid.url", function(e) {
      error("error");
      notify.ajaxError(e);
    })
    .success(function(resp) {
      error(false);
      serverDetails(details);
      util.showSavedIndicator(resp);
    })
    .call();
  }

  // Channel is a very simple abstraction on top
  // of the hub.
  var hubFilter = _.uniqueId( "municipality-maps-");

  function channel( sender ) {
    return {
      send: function( msg ) {
        hub.send( hubFilter, {
          sender: sender,
          message: msg} );
        }
    };
  }

  self.readOnly = ko.pureComputed( function() {
    return !lupapisteApp.models.globalAuthModel.ok( "upsert-organization-user");
  });

  // Parameter providers
  self.getParameters = function() {
    var ss = storedSettings();
    return {
      server: {
        readOnly: self.readOnly,
        server: ss.server,
        waiting: waiting,
        error: error,
        errorMessageTerm: errorMessageTerm,
        channel: channel( "server")
      },
      layers: {
        readOnly: self.readOnly,
        userLayers: ss.layers,
        serverLayers: serverLayers,
        backgroundVisible: backgroundMapVisible,
        channel: channel( "layers")
      },
      map: {
        PROXY: PROXY,
        capabilities: capabilities,
        serverLayers: serverLayers,
        userLayers: ss.layers,
        mapFitted: mapFitted,
        backgroundVisible: backgroundMapVisible
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


  var hubId = hub.subscribe( hubFilter, function( data ) {
    messageFuns[data.sender]( data.message);
  });

  self.dispose = _.partial( hub.unsubscribe, hubId );
};
