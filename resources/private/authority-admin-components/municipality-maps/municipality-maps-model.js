
LUPAPISTE.MunicipalityMapsModel = function() {
  "use strict";
  var self = this;
  var service = new LUPAPISTE.MunicipalityMapsService();

  var params = service.getParameters();
  self.serverParams = params.server;
  self.serverParams.header = "auth-admin.municipality-maps.server-details";
  self.serverParams.urlLabel = "auth-admin.municipality-maps.url";
  self.serverParams.saveLabel = "auth-admin.municipality-maps.fetch";
  self.serverParams.prefix = "munimap";
  self.layersParams = params.layers;
  self.mapParams    = params.map;

  self.dispose = service.dispose;
};
