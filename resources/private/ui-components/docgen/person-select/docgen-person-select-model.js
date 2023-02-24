LUPAPISTE.DocgenPersonSelectModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  // selectValue is a proxy for the underlying document data service
  // value. This approach allows for canceling.
  self.selectValue = ko.observable( self.value() );
  var partiesModel = _.get(params, "docModel.partiesModel");
  self.documentSchema = _.get(params, "docModel.schema");

  self.isDesignerDocument = _.get(self.documentSchema, "info.type") === "party" &&
                            _.get(self.documentSchema, "info.subtype") === "suunnittelija";

  self.appId = _.get(partiesModel, "applicationId", params.applicationId);
  self.myNs = self.path.slice(0, self.path.length - 1).join(".");

  var collectionFn = _.get(params, "docModel.getCollection") || function() { return "documents"; };
  var collection = collectionFn();

  self.personOptions = self.disposedComputed( function() {
    var items = _.clone (util.getIn(partiesModel, ["personSelectorItems"], []));
    if( params.schema.excludeCompanies ) {
      _.remove( items, "company" );
    }
    return items;
  });

  self.optionsText = function( person ) {
    if (person.id) {
      var name = person.lastName + " " + person.firstName;
      if (person.company) {
        return name + ", " + person.company.name;
      } else {
        return name;
      }
    }
  };

  self.doSetUserToDocument = function(value) {
    ajax.command("set-user-to-document", { id: self.appId, documentId: self.documentId, userId: value, path: self.myNs, collection: collection })
      .success( _.partial( repository.load, self.appId, _.noop))
      .error(function(e) {
        if (e.text !== "error.application-does-not-have-given-auth") {
          error("Failed to set user to document", value, self.docId, e);
        }
        notify.ajaxError(e);
      })
      .call();
  };

  self.setUserToDocumentOk = ko.observable(false);

  function showDialog( email ) {
    hub.send( "show-dialog", {ltitle: "application.addInvite",
                              size: "autosized",
                              component: "authorize-person-dialog",
                              componentParams: {documentName: _.get(self.documentSchema, "info.name"),
                                                path: self.myNs,
                                                documentId: self.documentId,
                                                email: email}});
  }

  if (self.authModel.ok("set-user-to-document")) {
    self.disposedSubscribe(self.selectValue, function(value) {
      if (value !== self.value()) {
        var selectedUser = _.find(self.personOptions(), ["id", value]);
        if (selectedUser && self.isDesignerDocument
            && selectedUser.notPersonallyAuthorized) {
          showDialog( selectedUser.email );
        } else {
          self.doSetUserToDocument(value || "");
        }
      }
    });
    self.setUserToDocumentOk(true);
  }

  self.inviteWithRoleOk = ko.observable(false);
  if (self.authModel.ok("invite-with-role")) {
    self.inviteWithRoleOk(true);
  }

  self.invite = function() {
    showDialog();
  };

};
