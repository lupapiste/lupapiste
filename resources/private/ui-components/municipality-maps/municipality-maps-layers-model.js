LUPAPISTE.MunicipalityMapsLayersModel = function( params ) {
  "use strict";
  var self = this;

  console.log( "Layers params:", params );
  // self.waiting = params.waiting;
  // self.error = params.error;
  self.serverLayers = params.serverLayers;
  self.userLayers = params.userLayers;

  //console.log( "Layers userLayers:", self.userLayers());
  self.shortLoc = function( id ) {
    return loc( "auth-admin.municipality-maps." + id );
  };

  // ko.computed( function() {
  //   if( params.settings() ) {
  //     var layers = params.settings().layers;
  //     if( layers ) {
  //       // Is this the proper way to copy observable tree while
  //       // retaining semantics?
  //       console.log( "Copy userLayers");
  //       self.userLayers(ko.mapping.fromJS( ko.mapping.toJS( layers))());
  //       console.log( "Copied:", ko.mapping.toJS( self.userLayers));
  //     }
  //   }
  // });

  // ko.computed( function() {
  //   if( self.userLayers() ) {
  //     params.channel.send( {layers: ko.mapping.toJS( self.userLayers )});
  //   }
  // });

  self.addLayer = function() {
    //self.userLayers(_.flatten([self.userLayers(), new params.Layer()]));
    self.userLayers.push( new params.Layer());
};

  self.removeLayer = function( layer ) {
    //self.userLayers( _.reject(self.userLayers(), layer));
    self.userLayers.remove( layer );
  };

};
