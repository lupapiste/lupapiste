LUPAPISTE.AutocompleteTagsModel = function() {
  "use strict";
  var self = this;

  self.selected = lupapisteApp.models.tagFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var result = [];

    for (var key in lupapisteApp.models.tagFilterService.data()) {
      var header = {label: lupapisteApp.models.tagFilterService.data()[key].name[loc.currentLanguage], groupHeader: true};

      var filteredData = util.filterDataByQuery(lupapisteApp.models.tagFilterService.data()[key].tags, self.query() || "", self.selected());
      // append group header and group items to result data
      if (filteredData.length > 0) {
        if (_.keys(lupapisteApp.models.tagFilterService.data()).length > 1) {
          result = result.concat(header);
        }
        result = result.concat(filteredData);
      }
    }
    return result;
  });
};
