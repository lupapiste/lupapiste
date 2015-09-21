;(function() {
  "use strict";

  function ApplicationsListModel() {
    var self = this;

    self.init = ko.observable(false);
    self.invitesModel = new LUPAPISTE.InvitesModel();
  }

  var model = new ApplicationsListModel();

  hub.onPageLoad("applications", function() {
    model.init(true);
  });

  $(function() {
    $("#applications").applyBindings(model);
  });

})();
