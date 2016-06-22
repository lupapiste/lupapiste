
LUPAPISTE.MunicipalityMapsModel = function() {
  "use strict";
  var self = this;
  var service = new LUPAPISTE.MunicipalityMapsService();

  var params = service.getParameters();
  self.serverParams = params.server;
  self.layersParams = params.layers;
  self.mapParams    = params.map;

  self.dispose = service.dispose;
};
