var docgen = (function () {
  "use strict";

  function displayDocuments(containerSelector, application, documents, authorizationModel, options) {
    function updateOther(select) {
      var otherId = select.attr("data-select-other-id"),
          other = $("#" + otherId, select.parent().parent());
      other.parent().css("visibility", select.val() === "other" ? "visible" : "hidden");
    }
    function initSelectWithOther(i, e) { updateOther($(e)); }
    function selectWithOtherChanged(event) {
      updateOther($(event.target));
    }

    var isDisabled = options && options.disabled;

    _.each($(".sticky", containerSelector), function(elem) {
        window.Stickyfill.remove(elem);
    });

    var docgenDiv = $(containerSelector).empty();

    _.each(documents, function (doc) {
      var schema = doc.schema;
      var docModel = new DocModel(schema, doc, application, authorizationModel, options);
      docModel.triggerEvents();

      docgenDiv.append(docModel.element);

      if (schema.info.repeating && !schema.info["no-repeat-button"] && !isDisabled && authorizationModel.ok("create-doc")) {
        var icon = $("<i>", {"class": "lupicon-circle-plus"});
        var span = $("<span>").text( loc(schema.info.name + "._append_label"));
        var btn = $("<button>",
                    {"data-test-id": schema.info.name + "_append_btn", "class": "secondary"})
          .click(function () {
            var self = this;
            ajax
              .command("create-doc", { schemaName: schema.info.name, id: application.id, collection: docModel.getCollection() })
              .success(function (resp) {
                var newDocId = resp.doc;
                var newDocSchema = _.cloneDeep(schema);
                newDocSchema.info.op = null;

                // The new document might contain some default values, get them from backend
                ajax.query("document", {id: application.id, doc: newDocId, collection: docModel.getCollection()})
                  .success(function(data) {
                    var newDoc = new DocModel(newDocSchema, data.document, application, authorizationModel);
                    newDoc.triggerEvents();

                    $(self).before(newDoc.element);
                    $(".sticky", newDoc.element).Stickyfill();

                    newDoc.showValidationResults(data.document.validationErrors);
                  })
                  .call();
              })
              .call();
          });
        btn.append( [icon, span]);
        docgenDiv.append(btn);
      }
    });

    $("select[data-select-other-id]", docgenDiv).each(initSelectWithOther).change(selectWithOtherChanged);
    $(".sticky", docgenDiv).Stickyfill();
    window.Stickyfill.rebuild();
  }

  return {
    displayDocuments: displayDocuments
  };

})();
