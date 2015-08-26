LUPAPISTE.AutocompleteTagsModel = function(params) {
  "use strict";
  var self = this;

  self.selected = params.selectedValues;
  self.query = ko.observable("");

  var tagsData = ko.observable();

  ajax
    .query("get-organization-tags")
    .error(_.noop)
    .success(function(res) {
      tagsData(res.tags);
    })
    .call();

  self.data = ko.pureComputed(function() {
    var result = [];
    var data = tagsData();

    for (var key in data) {
      var header = {label: data[key].name[loc.currentLanguage], groupHeader: true};

      var filteredData = util.filterDataByQuery(data[key].tags, self.query() || "", self.selected());
      // append group header and group items to result data
      if (filteredData.length > 0) {
        if (_.keys(data).length > 1) {
          result = result.concat(header);
        }
        result = result.concat(filteredData);
      }
    }
    return result;
  });
};
