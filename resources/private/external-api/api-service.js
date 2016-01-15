LUPAPISTE.ExternalApiService = function() {
  "use strict";
 /**
  * @typedef PermitFilter
  * @type {object}
  * @property {string} id - asiointitunnus
  * @property {string} address - address or description of the permit
  * @property {string} applicant - applicant's lastname and firstname concatenated
  * @property {string} authority - authority's lastname and firstname concatenated
  * @property {object} location - location object with properties 'x' and 'y' (EPSG:3067 / TM35FIN)
  * @property {string} municipality - municipality code of the permit
  * @property {string} type - is the permit 'application' or 'inforequest'
  * @property {string} operation - primary operation name localized in user's language
  */

  /*
   * LupapisteApi.showPermitOnMap
   * Description: Show permit on map using location info
   * @param {PermitFilter}
   */
  hub.subscribe("external-api::show-on-map", function(data) {
    var eventData = _.omit(data, "eventType"); // drop event type
    window.parent.LupapisteApi.showPermitOnMap(eventData);
  });

  /*
   * LupapisteApi.openPermit
   * Description: Open permit, provides ID of the permit.
   * @param {string} id id of the permit (ie. 'LP-123-2016-00001')
   * @param {string} type'application' OR 'inforequest'
   */
  hub.subscribe("external-api::open-application", function(data) {
    var id = _.get(data, "id");
    var type = _.get(data, "type");
    window.parent.LupapisteApi.openPermit(id, type);
  });

  /*
   * LupapisteApi.showPermitsOnMap
   * Description: Show given permits on map.
   * @param {Array<PermitFilter>} data Array of PermitFilter objects
   */
  hub.subscribe("external-api::filtered-permits", function(data) {
    var eventData = _.omit(data, "eventType"); // drop event type
    window.parent.LupapisteApi.showPermitsOnMap(eventData);
  });

  /*
   * LupapisteApi.integrationSent
   * Description: Function is called when integration (KRYSP) was successully sent.
   * @param {string} id Lupapiste ID for permit
   */
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
