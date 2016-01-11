LUPAPISTE.MunicipalityMapsLayersModel = function( params ) {
  "use strict";
  var self = this;

  self.readOnly = params.readOnly;
  self.serverLayers = params.serverLayers;
  self.userLayers = params.userLayers;
  self.backgroundVisible = params.backgroundVisible;

  self.shortLoc = function( id ) {
    return loc( "auth-admin.municipality-maps." + id );
  };

  self.addLayer = function() {
    params.channel.send( {op: "add"});
  };

  self.removeLayer = function( layer ) {
    params.channel.send( {op: "remove", layer: layer});
  };

};
