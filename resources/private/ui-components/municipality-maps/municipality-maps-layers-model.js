LUPAPISTE.MunicipalityMapsLayersModel = function( params ) {
  "use strict";
  var self = this;

  self.serverLayers = params.serverLayers;
  self.userLayers = params.userLayers;

  self.shortLoc = function( id ) {
    return loc( "auth-admin.municipality-maps." + id );
  };

  self.addLayer = function() {
    params.channel.send( {op: "addLayer"});
  };

  self.removeLayer = function( layer ) {
    params.channel.send( {op: "removeLayer", layer: layer});
  };

};
