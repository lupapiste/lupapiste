LUPAPISTE.ExternalApiService = function() {
  "use strict";

  hub.subscribe("external-api::show-on-map", function(data) {
    var eventData = _.omit(data, "type"); // drop event type
    window.parent.LupapisteApi.showPermitOnMap(eventData);
  });

  hub.subscribe("external-api::open-application", function(data) {
    var eventData = _.get(data, "id");
    window.parent.LupapisteApi.openInSitoGis(eventData);
  });

  hub.subscribe("external-api::filtered-permits", function(data) {
    var eventData = _.omit(data, "type"); // drop event type
    window.parent.LupapisteApi.showPermitsOnMap(eventData);
  });

  hub.subscribe("external-api::integration-sent", function(data) {
    var id = _.get(data, "id");
    window.parent.LupapisteApi.integrationSent(id);
  });

  return {
    enabled: function() {
      return window.parent.LupapisteApi;
    }
  };
};

lupapisteApp.services.externalApiService = new LUPAPISTE.ExternalApiService();
