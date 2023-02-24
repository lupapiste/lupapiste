(function() {
  "use strict";

  function PropertyFormationAppModel() {
    var self = this;
    self.sourceAppId = ko.observable("");
    self.propertyInfo = ko.observable();
    self.inviteCandidates = ko.observableArray([]);
    self.selectedInviteCandidates = ko.pureComputed(function() {
      return _.filter(self.inviteCandidates(), function(candidate) {
        return candidate.selected();
      });
    });

    self.processing = ko.observable(false);
    self.pending = ko.observable(false);
    self.waiting = ko.observable(false);
    self.fetchingCandidates = ko.observable(false);

    function getPropertyInfo() {
      var application = self.sourceAppId();
      if (application) {
       ajax.query("property-info", {id: application})
         .success(function(data) {
           var property = data.propertyInfo;
           var municipality = property.municipality ? loc(["municipality", property.municipality]): "";
           var propertyId = property.propertyId ? util.prop.toHumanFormat(property.propertyId) + ", ": "";
           var address = property.address ? property.address + ", ": "";
           self.propertyInfo(propertyId + address + municipality);
         })
         .call();
      }
    }

    function fetchAuthCandidatesForApplication() {
      var application = self.sourceAppId();
      if (application) {
        ajax.query("property-formation-app-invite-candidates", {id: application})
          .processing(self.fetchingCandidates)
          .pending(self.fetchingCandidates)
          .success(function(d) {
            self.inviteCandidates(_.map(d.candidates, function(candidate) {
              candidate.selected = ko.observable(false);
              return candidate;
            }));
          })
          .error(function(d) {
            notify.ajaxError(d);
          })
          .call();
      }
    }

    self.authDescription = function(auth) {
      var name = (auth.firstName !== "" ?
          (auth.firstName + (auth.lastName !== "" ? (" " + auth.lastName) : "")) :
          (auth.email !== null ? auth.email : ""));
      return name + ", " +
          _.upperFirst(auth.roleSource === "document" ?
              loc("applicationRole." + auth.role)
              : (auth.role === "reader" ?
                  loc("authorityrole." + auth.role) : loc(auth.role)));
    };

    self.clearCandidates = function () {
      return self.inviteCandidates([]);
    };

    self.clear = function() {
      self.sourceAppId(pageutil.lastSubPage());
      getPropertyInfo();
      self.clearCandidates();
      fetchAuthCandidatesForApplication();
    };

    self.goBackToApp = function () {
      pageutil.openApplicationPage({id: self.sourceAppId()});
    };

    self.doCreatePropertyFormationApp = function () {
      ajax.command("create-property-formation-app", {id: self.sourceAppId(), invites: _.map(self.selectedInviteCandidates(), "id")})
        .processing(self.processing)
        .pending(self.pending)
        .success(function(data) {
          pageutil.openApplicationPage(data);
          hub.send("indicator", {style: "positive",
            message: "createPropertyFormationApp.success.text",
            sticky: true});
        })
        .call();
    };

    self.createPropertyFormationApp = function() {
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("createPropertyFormationApp.confirmation.title"),
        loc("createPropertyFormationApp.confirmation.message"),
          {title: loc("yes"), fn: self.doCreatePropertyFormationApp},
          {title: loc("no")}
      );
    };

  }

  var model = new PropertyFormationAppModel();

  hub.onPageLoad("property-formation-app", model.clear);

  $(function() {
    $("#property-formation-app").applyBindings(model);
  });

})();