/**
 * Works as Facade for window.parent.LupapisteApi external API
 * by subscribing to hub's external-api events.
 */
LUPAPISTE.ExternalApiService = function() {
  "use strict";

 /**
  * Permit data sent via external API.
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
  * @property {string} permitType - application's permit type
  */

  /*
   * LupapisteApi.showPermitOnMap
   * Description: Show permit on map using location info
   * @param {PermitFilter}
   */
  hub.subscribe("external-api::show-on-map", function(data) {
    var permit = _.omit(data, "eventType"); // drop event type
    if (window.parent.LupapisteApi.showPermitOnMap) {
      window.parent.LupapisteApi.showPermitOnMap(permit);
    }
  });

  /*
   * LupapisteApi.openPermit
   * Description: Open permit.
   * @param {PermitFilter}
   */
  hub.subscribe("external-api::open-application", function(data) {
    var permit = _.omit(data, "eventType"); // drop event type
    if (window.parent.LupapisteApi.openPermit) {
      window.parent.LupapisteApi.openPermit(permit);
    }
  });

  /*
   * LupapisteApi.showPermitsOnMap
   * Description: Show given permits on backend map (button in application list view).
   * @param {Array<PermitFilter>} data Array of PermitFilter objects
   */
  hub.subscribe("external-api::filtered-permits", function(data) {
    var eventData = _.omit(data, "eventType"); // drop event type
    var permits = _.values(eventData);
    if (window.parent.LupapisteApi.showPermitsOnMap) {
      window.parent.LupapisteApi.showPermitsOnMap(permits);
    }
  });

  /*
   * LupapisteApi.integrationSent
   * Description: Permit is emited when integration (KRYSP) message was created successfully.
   * @param {PermitFilter}
   */
  hub.subscribe("external-api::integration-sent", function(data) {
    var permit = _.omit(data, "eventType"); // drop event type
    if (window.parent.LupapisteApi.integrationSent) {
      window.parent.LupapisteApi.integrationSent(permit);
    }
  });

  var enabled = _.isFunction(window.parent.LupapisteApi);
  return { enabled: enabled};
};

lupapisteApp.services.externalApiService = new LUPAPISTE.ExternalApiService();
