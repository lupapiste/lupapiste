LUPAPISTE.AutocompleteAreasModel = function() {
  "use strict";
  var self = this;

  self.selected = lupapisteApp.models.areaFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.computed(function() {
    var result = [];

    _.forEach(lupapisteApp.models.areaFilterService.data(), function(org) {
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
          if (_.keys(lupapisteApp.models.areaFilterService.data()).length > 1) {
            result = result.concat(header);
          }
          result = result.concat(filteredData);
        }
      }
    });
    return result;
  });
};
