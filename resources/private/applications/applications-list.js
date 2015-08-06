;(function() {
  "use strict";

  function InvitesModel() {
    var self = this;

    function getHeaderText(inv) {
      var address = inv.application.address;
      var municipality = inv.application.municipality;
      var operation = util.getIn(inv, ["application", "primaryOperation", "name"]);
      return loc("auth") + ": " +
             (address ? address + ", " : "") +
             (municipality ? loc(["municipality", municipality]) + ", " : "") +
             (operation ? loc(["operations", operation]) : "");
    }

    self.invites = ko.observableArray([]);
    self.updateInvites = function() {
      invites.getInvites(function(data) {
        var invs = _(data.invites).map(function(inv) {
          return _.assign(inv, { headerText: getHeaderText(inv) });
        }).value();

        self.invites(invs);
      });
    };
    self.approveInvite = function(model) {
      hub.send("track-click", {category:"Applications", label:"", event:"approveInvite"});
      ajax
        .command("approve-invite", {id: model.application.id})
        .success(self.updateInvites)
        .call();
      return false;
    };

    var acceptDecline = function(application) {
      hub.send("track-click", {category:"Applications", label:"", event:"declineInvite"});
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

  }

  var model = new InvitesModel();

  hub.onPageLoad("applications", function() {
    model.updateInvites();
  });
  $(function() {
    $("#applications").applyBindings(model);
  });

})();
