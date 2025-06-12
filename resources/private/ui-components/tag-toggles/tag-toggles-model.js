// Radio or checkbox group rendered as tag buttons.
// Parameters [optional]:
//
// selected: Observable that contains the selected value (radio group) or values (checkbox group)
// [prefix]: Toggle class prefix. Wrapper class is prefix-wrapper and label prefix-label (default "plain-tag").
// toggles: List of toggle definitions:
//           value: Toggle value
//           click: Function to be called when selected. Gets value as argument (optional).
//           lText or text: Label l10n key or label text. Former overrides latter.
//           icon: lupicon class name (optional)
//           prefix: Overrides top-level prefix (optional)
//           testId: Label test id. Input test id is testId-input (optional)

LUPAPISTE.TagTogglesModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.toggleType = params.type || "radio";

  // Selected is either an individual value (for radio groups) or list.
  self.selected = params.selected;
  self.groupName = _.uniqueId( "tag-toggle-group");

  self.toggles = self.disposedComputed( function() {
    return _.map( ko.unwrap( params.toggles ),
                  function( t ) {
                    return {text: t.text || loc( t.lText ),
                            value: t.value,
                            icon: t.icon,
                            click: t.click,
                            id: _.uniqueId( "tag-toggle"),
                            prefix: t.prefix || params.prefix || "plain-tag",
                            testId: t.testId || _.uniqueId( "tag-toggle-test")};
                  });
  });

  self.disposedSubscribe( self.selected,
                          function( value ) {
                            var toggle = _.find( self.toggles(), {value: value});
                            var fun = _.get( toggle, "click", _.noop );
                            fun( value );
                          });
};
