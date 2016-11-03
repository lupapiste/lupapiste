LUPAPISTE.AutocompleteRecipientModel = function(params) {
  "use strict";
  var self = this;

  self.lPlaceholder = params.lPlaceholder;

  self.selected = lupapisteApp.services.assignmentRecipientFilterService.selected;

  self.query = ko.observable("");

  self.data = ko.pureComputed(function() {
    return _(util.filterDataByQuery({data: lupapisteApp.services.assignmentRecipientFilterService.data(),
                                     query: self.query(),
                                     selected: self.selected(),
                                     label: "fullName"}))
      .sortBy("fullName")
      .filter(function(o) { return o.id != util.getIn(lupapisteApp.models.currentUser, ["id"]); })
      .unshift(lupapisteApp.services.assignmentRecipientFilterService.myown)
      .unshift(lupapisteApp.services.assignmentRecipientFilterService.all)
      .value();
  });
};
