LUPAPISTE.AutocompleteCompanyTagsModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;
  self.disable = params.disable || false;

  self.selected = lupapisteApp.services.companyTagFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    var result = [];
    var tagGroups = lupapisteApp.services.companyTagFilterService.data();

    _.each(tagGroups, function(tagGroup) {

      var filteredData = util.filterDataByQuery({data: tagGroup.tags,
                                                 query: self.query(),
                                                 selected: self.selected()});

      if (filteredData.length > 0) {

        result = result.concat(_.sortBy(filteredData, "label"));
      }
    });
    return result;

  });
};
