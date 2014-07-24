var municipalities = (function() {
  "use strict";

  var municipalities = ko.observable();
  var municipalitiesById = ko.observable();

  function findById(id, callback, context) {
    if (!_.isFunction(callback)) { throw "callback must be a function: " + callback; }
    if (!id) {
      callback.call(context, null);
      return context;
    }
    var m = municipalitiesById()[id];
    // TODO: Implement and use search to find municipality data for unsupported municipalities too.
    callback.call(context, m);
    return context;
  }

  function findByLocation(x, y, callback, context) {
    if (!_.isFunction(callback)) { throw "callback must be a function: " + callback; }
    if (!x || !y) {
      callback.call(context, null);
      return context;
    }
    ajax
      .query("municipality-by-location", {x: x, y: y})
      .success(function(data) {
        var m = data.municipality;
        var s = municipalitiesById()[m.id];
        if (s) {
          m = s;
        } else {
          m.supported = false;
        }
        callback.call(context, m);
      })
      .call();
    return context;
  }

  function reset(ms) {
    municipalitiesById(_.reduce(ms, function(d, m) { d[m] = {supported: true, id: m}; return d; }, {}));
    municipalities(_.sortBy(_.values(municipalitiesById()), function(m) { return loc(["municipality", m.id]); }));
  }

  function operationsForMunicipality(municipality, callback, context) {
    if (!_.isFunction(callback)) { throw "callback must be a function: " + callback; }
    ajax
      .query("selected-operations-for-municipality", {municipality: municipality})
      .success(function(data) {
        var operations = data.operations;
        callback.call(context, operations);
      })
      .call();
    }

  function init() {
    ajax
      .query("municipalities-with-organization")
      .success(function(data) { reset(data.municipalities); })
      .call();
  }

  init();

  return {

    // Observable containing a list of supported municipalities,
    // sorted alphabetically by name:

    municipalities: municipalities,

    // Observable containing a map of municipalities keyed by
    // municipality id (id = string of three digits):

    municipalitiesById: municipalitiesById,

    // Find municipality by ID. Calls callback with municipality.
    // Provided municipality has field "supported" set to true if municipality is supported:

    findById: findById,

    // Find municipality by location. Calls callback with municipality. Provided municipality
    // has field "supported" set to true if municipality is supported:

    findByLocation: findByLocation,

    // Gets the operations supported in the municipality by all organizations
    operationsForMunicipality: operationsForMunicipality

  };

})();
