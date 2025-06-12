var municipalities = (function() {
  "use strict";

  var municipalities = ko.observable();
  var municipalitiesById = ko.observable();
  var municipalitiesWithBackendSystemById = ko.observable();
  var municipalitiesWithBackendSystem = ko.observable();

  function findById(id, callback) {
    if (!_.isFunction(callback)) { throw "callback must be a function: " + callback; }
    if (!id) { callback(null); }
    // TODO: Implement and use search to find municipality data for unsupported municipalities too.
    callback( municipalitiesById()[id] );
  }

  function reset(ms, msWithBackendInUse) {
    var makeMsById = function(munis) {
      return _.reduce(munis, function(d, m) {
        d[m] = {supported: true, id: m, name: loc(["municipality", m])};
        return d; }, {});
    };
    var sortMunis = function(munis) { return _.sortBy(_.values(munis), "name"); };
    municipalitiesById( makeMsById(ms) );
    municipalities( sortMunis(municipalitiesById()) );
    municipalitiesWithBackendSystemById( makeMsById(msWithBackendInUse) );
    municipalitiesWithBackendSystem( sortMunis(municipalitiesWithBackendSystemById()) );
  }

  // TODO: Use requestContext here, like in locationSearch component?
  function operationsForMunicipality(municipality, callback) {
    if (!_.isFunction(callback)) { throw "callback must be a function: " + callback; }
    ajax
      .query("selected-operations-for-municipality", {municipality: municipality})
      .success(function(data) {
        callback(data.operations, data["operation-infos"]);
      })
      .call();
    }

  function init() {
    ajax
      .query("municipalities-with-organization")
      .success(function(data) { reset(data.municipalities, data.municipalitiesWithBackendInUse); })
      .call();
  }

  init();

  return {

    // Observable containing a list of supported municipalities,
    // sorted alphabetically by name:

    municipalities: municipalities,

    // Observable containing a list of supported municipalities that use a backing system,
    // sorted alphabetically by name:

    municipalitiesWithBackendSystem: municipalitiesWithBackendSystem,

    // Observable containing a map of municipalities keyed by
    // municipality id (id = string of three digits):

    municipalitiesById: municipalitiesById,

    // Find municipality by ID. Calls callback with municipality.
    // Provided municipality has field "supported" set to true if municipality is supported:

    findById: findById,

    // Gets the operations supported in the municipality by all organizations
    operationsForMunicipality: operationsForMunicipality

  };

})();
