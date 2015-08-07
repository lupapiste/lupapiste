;(function() {
  "use strict";

  function ApplicationsListModel() {
    var self = this;

    self.invitesModel = new LUPAPISTE.InvitesModel();
  }

  var model = new ApplicationsListModel();

  hub.onPageLoad("applications", function() {
    model.invitesModel.updateInvites();
  });

  $(function() {
    $("#applications").applyBindings(model);
  });

})();
