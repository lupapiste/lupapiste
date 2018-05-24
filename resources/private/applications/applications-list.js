;(function() {
  "use strict";

  function ApplicationsListModel() {
    var self = this;

    self.init = lupapisteApp.models.globalAuthModel.isInitialized;
    self.invitesModel = new LUPAPISTE.InvitesModel();
  }

  var model = new ApplicationsListModel();

  $(function() {
    $("#applications").applyBindings(model);
  });

})();
