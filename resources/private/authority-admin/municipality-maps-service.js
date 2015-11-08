LUPAPISTE.MunicipalityMapsService = function() {
  "use strict";
  var self = this;

  var PROXY = "/proxy/organization-map-server";

  function Layer( opts) {
    opts = _.defaults(opts || {}, {name: "", id: "", preview: true,
                                   fixed: false});
    this.name = ko.observable( opts.name );
    this.id = ko.observable( opts.id );
    this.preview = ko.observable( opts.preview );
    this.fixed = opts.fixed;
  }

  var serverDetails = ko.observable();
  var userLayers = ko.observableArray();

  var error = ko.observable();
  var waiting = ko.observable( false );
  var capabilities = ko.observable();
  var mapFitted = ko.observable( false );

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

  var storedSettings = ko.computed( {
    read: function() {
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
        }
      })
      .error( function() {
        error( true );
        resetUserLayers();
      })
      .call();
      return {server: serverDetails,
             layers: userLayers};
  },
  deferEvaluation: true
  });

  ko.computed( function () {
    if( _.size( userLayers() )) {
    ajax.command( "update-user-layers", {layers: _.map( userLayers(),
                  function( layer ) {
                    return {
                      name: layer.name(),
                      id: layer.id()
                    };
                  })}).call();
    }
  });

  function updateServerDetails( details ) {
    ajax.command( "update-map-server-details",
                  details)
    .pending( waiting )
    .success( function() {
      error( false );
    })
    .error( function() {
      error( true);
    })
    .complete( function() {
      serverDetails( details );
    })
    .call();
  }

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
    server: function( m ) {
      if( !_.isEqual( m, serverDetails())) {
        error( false );
        capabilities( null );
        resetUserLayers();
        mapFitted( false );
        updateServerDetails( m );
      }
    },
    layers: function( m ) {
      if( m.op === "addLayer") {
        userLayers.push( new Layer() );
      }
      if( m.op === "removeLayer" ) {
        userLayers.remove( m.layer );
      }
    }
  };


  hub.subscribe( hubId, function( data ) {
    messageFuns[data.sender]( data.message);
  });
};
