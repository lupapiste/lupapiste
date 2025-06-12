LUPAPISTE.AutocompleteTagsModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;
  self.disable = params.disable || false;

  self.selected = lupapisteApp.services.tagFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var result = [];
    var tagGroups = lupapisteApp.services.tagFilterService.data();

    _.each(tagGroups, function(tagGroup) {
      var header = {label: tagGroup.name[loc.currentLanguage], groupHeader: true};

      var filteredData = util.filterDataByQuery({data: tagGroup.tags,
                                                 query: self.query(),
                                                 selected: self.selected()});
      // append group header and group items to result data
      if (filteredData.length > 0) {
        if (_.keys(lupapisteApp.services.tagFilterService.data()).length > 1) {
          result = result.concat(header);
        }
        result = result.concat(_.sortBy(filteredData, "label"));
      }
    });
    return result;
  });
};
