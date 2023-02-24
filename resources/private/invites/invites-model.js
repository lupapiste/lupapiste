LUPAPISTE.InvitesModel = function() {
"use strict";
  var self = this;

  function getHeaderText(inv) {
    var address = inv.application.address;
    var municipality = inv.application.municipality;
    var operation = util.getIn(inv, ["application", "primaryOperation", "name"]);
    return (inv.type === "company" ? loc("company-auth") : loc("auth")) + ": " +
           (address ? address + ", " : "") +
           (municipality ? loc(["municipality", municipality]) + ", " : "") +
           (operation ? loc(["operations", operation]) : "");
  }

  self.invites = ko.observableArray([]);
  self.updateInvites = function() {
    invites.getInvites(function(data) {
      var invs = _(data.invites).map(function(inv) {
        return _.assign(inv, { headerText: getHeaderText(inv),
                               created: inv.created });
      }).value();

      self.invites(invs);
    });
  };
  self.approveInvite = function(model) {
    ajax
      .command("approve-invite", {id: model.application.id, "invite-type": model.type})
      .success(self.updateInvites)
      .call();
    return false;
  };

  var acceptDecline = function(application) {
      return function() {
          ajax
          .command("decline-invitation", {id: application.id})
          .success(self.updateInvites)
          .call();
          return false;
      };
  };

  self.declineInvite = function(model) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("applications.declineInvitation.header"),
      loc("applications.declineInvitation.message"),
      {title: loc("yes"), fn: acceptDecline(model.application)},
      {title: loc("no")}
    );
  };

  hub.onPageLoad("applications", function() {
    self.updateInvites();
  });

};
