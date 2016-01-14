LUPAPISTE.ExternalApiService = function() {
  "use strict";

  /*
   * LupapisteApi.showPermitOnMap
   * Description: Show permit on map using location info
   * Parameters:
   *   Object, where properties are:
   *     id (String): id of the permit (ie. 'LP-123-2016-00001')
   *     address (String): address or description of the permit
   *     applicant (String): applicant's lastname and firstname concatenated
   *     authority (String): authority's lastname and firstname concatenated
   *     location (Object): location object with properties 'x' and 'y' (EPSG:3067 / TM35FIN)
   *     municipality (String): municipality code of the permit
   *     operation (String): primary operation name localized in user's language
   */
  hub.subscribe("external-api::show-on-map", function(data) {
    var eventData = _.omit(data, "type"); // drop event type
    window.parent.LupapisteApi.showPermitOnMap(eventData);
  });

  hub.subscribe("external-api::open-application", function(data) {
    var id = _.get(data, "id");
    window.parent.LupapisteApi.openPermit(id);
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
