LUPAPISTE.DocgenPersonSelectModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.DocgenInputModel( params ));

  // selectValue is a proxy for the underlying document data service
  // value. This approach allows for canceling.
  self.selectValue = ko.observable( self.value() );
  var partiesModel = _.get(params, "docModel.partiesModel");
  var documentSchema = _.get(params, "docModel.schema");

  self.isDesignerDocument = _.get(documentSchema, "info.type") === "party" &&
                            _.get(documentSchema, "info.subtype") === "suunnittelija";

  self.appId = partiesModel.applicationId;
  self.myNs = self.path.slice(0, self.path.length - 1).join(".");

  var collectionFn = _.get(params, "docModel.getCollection") || function() { return "documents"; };
  var collection = collectionFn();

  self.personOptions = self.disposedComputed( function() {
    var itemsObservable = _.get(partiesModel, "personSelectorItems");
    return itemsObservable ? itemsObservable() : [];
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

  if (self.authModel.ok("set-user-to-document")) {
    self.disposedSubscribe(self.selectValue, function(value) {
      if (value !== self.value() && !_.isEmpty(value)) {
        var selectedUser = _.find(self.personOptions(), ["id", value]);
        console.log(selectedUser);
        if (self.isDesignerDocument && selectedUser.notPersonallyAuthorized) {
          LUPAPISTE.ModalDialog.showDynamicYesNo(loc("areyousure"),
                                                 loc("document.party.person-select.designer.areyousure"),
                                                 {title: loc("yes"), fn: _.partial(self.doSetUserToDocument, value)});
        } else {
          self.doSetUserToDocument(value);
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
    $("#invite-document-name").val(self.schemaName).change();
    $("#invite-document-path").val(self.myNs).change();
    $("#invite-document-id").val(self.documentId).change();
    LUPAPISTE.ModalDialog.open("#dialog-valtuutus");
  };

};