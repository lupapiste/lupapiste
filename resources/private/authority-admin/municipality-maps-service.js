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

  // var url = ko.observable("");
  // var username = ko.observable("");
  // var password = ko.observable("");
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


  // function fetchCapabilities() {
  //   $.get( PROXY,
  //          {request: "GetCapabilities",
  //           service: "wms"},
  //          function( data) {
  //            self.error( false );
  //            var parser = new ol.format.WMSCapabilities();

  //            capabilities(parser.read(data));
  //          },
  //          "text")
  //   .fail ( function( res ) {
  //     self.error( true );
  //     console.log( res );
  //   });
  // }

  ko.computed( function() {
    if( serverDetails()) {
      $.get( PROXY,
             {request: "GetCapabilities",
              service: "wms"},
             function( data ) {
               error( false );
               var parser = new ol.format.WMSCapabilities();
               console.log( "Capabilities update.");
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
        console.log( "Compute serverLayers");
        return namedLayers (caps.Capability.Layer);
      }
    },
    deferEvaluation: true
  });

  // function endpoint( ajaxFun, name, successFun, paramsFun) {
  //   return function () {
  //     var restParams = _.defaults( (paramsFun || _.noop )() || {},
  //                                  {pending: true});
  //     var ajx = ajaxFun( name, _.omit(restParams, "pending") );
  //     if( restParams.pending ) {
  //       ajx.pending( self.waiting );
  //     }
  //     ajx.success( function( res ) {
  //       error( false );
  //       successFun( res );
  //     } )
  //     .error( function () {
  //       error( true );
  //     })
  //     .call();
  //   };
  // }


  var storedSettings = ko.computed( {
    read: function() {
      ajax.query( "get-map-layers-data" )
      .pending( waiting )
      .success( function( res ) {
        console.log( "Stored settings:", res );
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
          console.log( "userLayers:", userLayers());
        }
      })
      .error( function() {
        error( true );
        userLayers( [new Layer( {name: "asemakaava",
                                 fixed: true}),
                     new Layer( {name: "kantakartta",
                                 fixed: true})]);
      })
      .call();
      return {server: serverDetails,
             layers: userLayers};
  },
  deferEvaluation: true
  });


  // var settings = { server: {url: ko.observable(),
  //                           username: ko.observable(),
  //                           password: ko.observable()},
  //                layers: ko.observable()};

  // // Representation of settings data that only includes
  // // the properties to be stored.
  // function sanitizeSettingsData( data ) {
  //   data = data || {};
  //   var sanitized = {};
  //   if( data.server ) {
  //     sanitized.server = _.pick( data.server, ["url", "username", "password"]);
  //   }
  //   if( data.layers) {
  //     sanitized.layers = _.map( data.layers, _.partialRight( _.pick, ["id", "name"]));
  //   }
  //   return sanitized;
  // }

  // var savedSettings = ko.computed( {
  //   read: function() {
  //     // Dependency
  //     //settings();
  //     ajax.query( "get-map-layers-data" )
  //     .pending( waiting )
  //     .success( function( res ) {
  //       error( false );
  //       var server = res.server || {};
  //       settings.server.url( server.url );
  //       settings.server.username( server.username );
  //       settings.server.password( server.password );
  //       if( _.size(res.layers) >= 2 ) {
  //         settings.layers( _.map( res.layers, function( layer, i ) {
  //           return new Layer({name: layer.name,
  //                             id: layer.id,
  //                             fixed: i < 2});
  //         }));
  //         mapFitted( false );
  //         //settings( obj );
  //       }
  //     })
  //     .error( function() {
  //       error( true );
  //       settings({
  //         server: ko.observable( {}),
  //         layers:  [new Layer( {name: "asemakaava",
  //                               fixed: true}),
  //                   new Layer( {name: "kantakartta",
  //                               fixed: true})]

  //       });
  //     })
  //     .call();
  //     return settings;
  //   },
  //   write: function( data ) {
  //     var newData = sanitizeSettingsData( data );
  //     var oldData = sanitizeSettingsData( ko.mapping.toJS( settings ));
  //     var changes = {};
  //     _.each( ["server", "layers"], function( key ) {
  //       if( newData[key] && !_.isEqual( oldData[key], newData[key])) {
  //         changes[key] = newData[key];
  //       }
  //     });
  //     if( _.size( changes )) {
  //       ajax.command( "update-map-layers-data", changes )
  //       .pending( waiting )
  //       .success( function() {
  //         error( false );
  //       })
  //       .error( function() {
  //         error( true );
  //       })
  //       .call();
  //     }
  //     // We always update the settings just in case.
  //     // This also ensures that preview information is up-to-date.
  //     settings( _.merge( settings(), ko.mapping.fromJS( data )));
  //   },
  //   deferEvaluation: true
  // });


  // self.updateServerDetails = endpoint( ajax.command,
  //                                      "update-map-server-details",
  //                                      fetchCapabilities,
  //                                      function() {
  //                                        return {url: self.url(),
  //                                                username: self.username(),
  //                                                password: self.password()};
  //                                      });


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

  ko.computed( function() {
    if( serverDetails() ) {
      ajax.command( "update-map-server-details",
                  serverDetails())
      .pending( waiting )
      .success( function() {
        error( false );
      })
      .error( function() {
        error( true)
      })
      .call();
    }
  });

  // var hubId = _.uniqueId( "municipality-maps-");
  // function channel() {
  //   return {
  //     send: function( msg ) {
  //       hub.send( hubId, {message: msg} );
  //       }
  //   };
  // }

  // Parameter providers
  self.getParameters = function() {
    var ss = storedSettings();
    return {
      server: {
        server: ss.server,
        waiting: waiting,
        error: error
      },
      layers: {
        Layer: Layer,
        userLayers: ss.layers,
        serverLayers: serverLayers
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

//   hub.subscribe( hubId, function( data ) {
//     console.log( "Data received:", data );
//     savedSettings( data.message );
//   });
};
