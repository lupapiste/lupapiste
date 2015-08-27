LUPAPISTE.AutocompleteAreasModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selectedValues;
  self.query = ko.observable("");

  var orgsAreas = ko.observable();

  ajax
    .query("get-organization-areas")
    .error(_.noop)
    .success(function(res) {
      orgsAreas(res.areas);
    })
    .call();

  self.data = ko.computed(function() {
    var result = [];

    _.forEach(orgsAreas(), function(org) {
      if (org.areas && org.areas.features) {
        var header = {label: org.name[loc.currentLanguage], groupHeader: true};


        var features = _.map(org.areas.features, function(feature) {
          for(var key in feature.properties) { // properties to lower case
            feature.properties[key.toLowerCase()] = feature.properties[key];
          }
          var nimi = util.getIn(feature, ["properties", "nimi"]);
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
