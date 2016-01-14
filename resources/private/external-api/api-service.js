LUPAPISTE.ExternalApiService = function() {
  "use strict";

  hub.subscribe("external-api::show-on-map", function(data) {
    var eventData = _.omit(data, "type"); // drop event type
    window.parent.LupapisteApi.showPermitOnMap(eventData);
  });

  hub.subscribe("external-api::open-application", function(data) {
    var eventData = _.omit(data, "type"); // drop event type
    window.parent.LupapisteApi.openInSitoGis(eventData);
  });

  return {
    enabled: function() {
      return window.parent.LupapisteApi;
    }
  };
};

lupapisteApp.services.externalApiService = new LUPAPISTE.ExternalApiService();
