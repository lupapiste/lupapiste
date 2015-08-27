LUPAPISTE.AutocompleteAreasModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selectedValues;
  self.query = ko.observable("");

  var orgsAreas = ko.observable();

  var defaultFilter = ko.pureComputed(function() {
    var applicationFilters = lupapisteApp.models.currentUser.applicationFilters;
    return applicationFilters && 
           applicationFilters()[0].filter.areas &&
           applicationFilters()[0].filter.areas() ||
           [];
  });

  ajax
    .query("get-organization-areas")
    .error(_.noop)
    .success(function(res) {
      orgsAreas(res.areas);

      ko.utils.arrayPushAll(self.selected,
        _(res.areas)
          .map('areas')
          .pluck('features')
          .flatten()
          .filter(function(feature) {
            return _.contains(defaultFilter(), feature.id);
          })
          .map(function(feature) {
            return {id: feature.id, label: util.getFeatureName(feature)};
          })
          .value());
    })
    .call();

  self.data = ko.computed(function() {
    var result = [];

    _.forEach(orgsAreas(), function(org) {
      if (org.areas && org.areas.features) {
        var header = {label: org.name[loc.currentLanguage], groupHeader: true};

        var features = _.map(org.areas.features, function(feature) {
          var nimi = util.getFeatureName(feature);
          var id = feature.id;
          return {label: nimi, id: id};
        });
        features = _.filter(features, "label");

        var filteredData = util.filterDataByQuery(features, self.query() || "", self.selected());
        // append group header and group items to result data
        if (filteredData.length > 0) {
          if (_.keys(orgsAreas()).length > 1) {
            result = result.concat(header);
          }
          result = result.concat(filteredData);
        }
      }
    });
    return result;
  });
};
