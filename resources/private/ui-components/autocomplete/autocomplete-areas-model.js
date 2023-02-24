LUPAPISTE.AutocompleteAreasModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;
  self.disable = params.disable || false;

  self.selected = lupapisteApp.services.areaFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.computed(function() {
    var result = [];

    _.forEach(lupapisteApp.services.areaFilterService.data(), function(org) {
      if (org.areas && org.areas.features) {
        var header = {label: org.name[loc.currentLanguage], groupHeader: true};

        var features = _.map(org.areas.features, function(feature) {
          var nimi = util.getFeatureName(feature);
          var id = feature.id;
          return {label: nimi, id: id};
        });
        features = _.filter(features, "label");

        var filteredData = util.filterDataByQuery({data: features,
                                                   query: self.query(),
                                                   selected: self.selected()});
        // append group header and group items to result data
        if (filteredData.length > 0) {
          if (_.keys(lupapisteApp.services.areaFilterService.data()).length > 1) {
            result = result.concat(header);
          }
          result = result.concat(_.sortBy(filteredData, "label"));
        }
      }
    });
    return result;
  });
};
