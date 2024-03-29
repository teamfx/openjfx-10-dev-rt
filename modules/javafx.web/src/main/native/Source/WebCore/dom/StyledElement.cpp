/*
 * Copyright (C) 1999 Lars Knoll (knoll@kde.org)
 *           (C) 1999 Antti Koivisto (koivisto@kde.org)
 *           (C) 2001 Peter Kelly (pmk@post.com)
 *           (C) 2001 Dirk Mueller (mueller@kde.org)
 * Copyright (C) 2004, 2005, 2006, 2008, 2010, 2014 Apple Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; see the file COPYING.LIB.  If not, write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301, USA.
 */

#include "config.h"
#include "StyledElement.h"

#include "CSSImageValue.h"
#include "CSSParser.h"
#include "CSSStyleSheet.h"
#include "CSSValuePool.h"
#include "CachedResource.h"
#include "ContentSecurityPolicy.h"
#include "DOMTokenList.h"
#include "HTMLElement.h"
#include "HTMLParserIdioms.h"
#include "InspectorInstrumentation.h"
#include "PropertySetCSSStyleDeclaration.h"
#include "ScriptableDocumentParser.h"
#include "StyleProperties.h"
#include "StyleResolver.h"
#include <wtf/HashFunctions.h>

namespace WebCore {

COMPILE_ASSERT(sizeof(StyledElement) == sizeof(Element), styledelement_should_remain_same_size_as_element);

using namespace HTMLNames;

void StyledElement::synchronizeStyleAttributeInternal(StyledElement* styledElement)
{
    ASSERT(styledElement->elementData());
    ASSERT(styledElement->elementData()->styleAttributeIsDirty());
    styledElement->elementData()->setStyleAttributeIsDirty(false);
    if (const StyleProperties* inlineStyle = styledElement->inlineStyle())
        styledElement->setSynchronizedLazyAttribute(styleAttr, inlineStyle->asText());
}

StyledElement::~StyledElement()
{
    if (PropertySetCSSStyleDeclaration* cssomWrapper = inlineStyleCSSOMWrapper())
        cssomWrapper->clearParentElement();
}

CSSStyleDeclaration* StyledElement::cssomStyle()
{
    return ensureMutableInlineStyle().ensureInlineCSSStyleDeclaration(this);
}

MutableStyleProperties& StyledElement::ensureMutableInlineStyle()
{
    RefPtr<StyleProperties>& inlineStyle = ensureUniqueElementData().m_inlineStyle;
    if (!inlineStyle)
        inlineStyle = MutableStyleProperties::create(strictToCSSParserMode(isHTMLElement() && !document().inQuirksMode()));
    else if (!is<MutableStyleProperties>(*inlineStyle))
        inlineStyle = inlineStyle->mutableCopy();
    return downcast<MutableStyleProperties>(*inlineStyle);
}

void StyledElement::attributeChanged(const QualifiedName& name, const AtomicString& oldValue, const AtomicString& newValue, AttributeModificationReason reason)
{
    if (oldValue != newValue) {
        if (name == styleAttr)
            styleAttributeChanged(newValue, reason);
        else if (isPresentationAttribute(name)) {
            elementData()->setPresentationAttributeStyleIsDirty(true);
            invalidateStyle();
        }
    }

    Element::attributeChanged(name, oldValue, newValue, reason);
}

PropertySetCSSStyleDeclaration* StyledElement::inlineStyleCSSOMWrapper()
{
    if (!inlineStyle() || !inlineStyle()->hasCSSOMWrapper())
        return 0;
    PropertySetCSSStyleDeclaration* cssomWrapper = ensureMutableInlineStyle().cssStyleDeclaration();
    ASSERT(cssomWrapper && cssomWrapper->parentElement() == this);
    return cssomWrapper;
}

static bool usesStyleBasedEditability(const StyleProperties& properties)
{
    return properties.getPropertyCSSValue(CSSPropertyWebkitUserModify);
}

void StyledElement::setInlineStyleFromString(const AtomicString& newStyleString)
{
    RefPtr<StyleProperties>& inlineStyle = elementData()->m_inlineStyle;

    // Avoid redundant work if we're using shared attribute data with already parsed inline style.
    if (inlineStyle && !elementData()->isUnique())
        return;

    // We reconstruct the property set instead of mutating if there is no CSSOM wrapper.
    // This makes wrapperless property sets immutable and so cacheable.
    if (inlineStyle && !is<MutableStyleProperties>(*inlineStyle))
        inlineStyle = nullptr;

    if (!inlineStyle)
        inlineStyle = CSSParser::parseInlineStyleDeclaration(newStyleString, this);
    else
        downcast<MutableStyleProperties>(*inlineStyle).parseDeclaration(newStyleString, document());

    if (usesStyleBasedEditability(*inlineStyle))
        document().setHasElementUsingStyleBasedEditability();
}

void StyledElement::styleAttributeChanged(const AtomicString& newStyleString, AttributeModificationReason reason)
{
    WTF::OrdinalNumber startLineNumber = WTF::OrdinalNumber::beforeFirst();
    if (document().scriptableDocumentParser() && !document().isInDocumentWrite())
        startLineNumber = document().scriptableDocumentParser()->textPosition().m_line;

    if (newStyleString.isNull()) {
        if (PropertySetCSSStyleDeclaration* cssomWrapper = inlineStyleCSSOMWrapper())
            cssomWrapper->clearParentElement();
        ensureUniqueElementData().m_inlineStyle = nullptr;
    } else if (reason == ModifiedByCloning || document().contentSecurityPolicy()->allowInlineStyle(document().url(), startLineNumber, String(), isInUserAgentShadowTree()))
        setInlineStyleFromString(newStyleString);

    elementData()->setStyleAttributeIsDirty(false);

    invalidateStyle();
    InspectorInstrumentation::didInvalidateStyleAttr(document(), *this);
}

void StyledElement::invalidateStyleAttribute()
{
    if (usesStyleBasedEditability(*inlineStyle()))
        document().setHasElementUsingStyleBasedEditability();

    elementData()->setStyleAttributeIsDirty(true);
    invalidateStyle();
}

void StyledElement::inlineStyleChanged()
{
    invalidateStyleAttribute();
    InspectorInstrumentation::didInvalidateStyleAttr(document(), *this);
}

bool StyledElement::setInlineStyleProperty(CSSPropertyID propertyID, CSSValueID identifier, bool important)
{
    ensureMutableInlineStyle().setProperty(propertyID, CSSValuePool::singleton().createIdentifierValue(identifier), important);
    inlineStyleChanged();
    return true;
}

bool StyledElement::setInlineStyleProperty(CSSPropertyID propertyID, CSSPropertyID identifier, bool important)
{
    ensureMutableInlineStyle().setProperty(propertyID, CSSValuePool::singleton().createIdentifierValue(identifier), important);
    inlineStyleChanged();
    return true;
}

bool StyledElement::setInlineStyleProperty(CSSPropertyID propertyID, double value, CSSPrimitiveValue::UnitType unit, bool important)
{
    ensureMutableInlineStyle().setProperty(propertyID, CSSValuePool::singleton().createValue(value, unit), important);
    inlineStyleChanged();
    return true;
}

bool StyledElement::setInlineStyleProperty(CSSPropertyID propertyID, const String& value, bool important)
{
    bool changes = ensureMutableInlineStyle().setProperty(propertyID, value, important, CSSParserContext(document()));
    if (changes)
        inlineStyleChanged();
    return changes;
}

bool StyledElement::removeInlineStyleProperty(CSSPropertyID propertyID)
{
    if (!inlineStyle())
        return false;
    bool changes = ensureMutableInlineStyle().removeProperty(propertyID);
    if (changes)
        inlineStyleChanged();
    return changes;
}

void StyledElement::removeAllInlineStyleProperties()
{
    if (!inlineStyle() || inlineStyle()->isEmpty())
        return;
    ensureMutableInlineStyle().clear();
    inlineStyleChanged();
}

void StyledElement::addSubresourceAttributeURLs(ListHashSet<URL>& urls) const
{
    auto* inlineStyle = this->inlineStyle();
    if (!inlineStyle)
        return;
    inlineStyle->traverseSubresources([&] (auto& resource) {
        urls.add(resource.url());
        return false;
    });
}

void StyledElement::rebuildPresentationAttributeStyle()
{
    RefPtr<StyleProperties> style = MutableStyleProperties::create(isSVGElement() ? SVGAttributeMode : HTMLQuirksMode);
    for (const Attribute& attribute : attributesIterator())
        collectStyleForPresentationAttribute(attribute.name(), attribute.value(), static_cast<MutableStyleProperties&>(*style));

    // ShareableElementData doesn't store presentation attribute style, so make sure we have a UniqueElementData.
    UniqueElementData& elementData = ensureUniqueElementData();

    elementData.setPresentationAttributeStyleIsDirty(false);
    elementData.m_presentationAttributeStyle = style->isEmpty() ? nullptr : WTFMove(style);
}

void StyledElement::addPropertyToPresentationAttributeStyle(MutableStyleProperties& style, CSSPropertyID propertyID, CSSValueID identifier)
{
    style.setProperty(propertyID, CSSValuePool::singleton().createIdentifierValue(identifier));
}

void StyledElement::addPropertyToPresentationAttributeStyle(MutableStyleProperties& style, CSSPropertyID propertyID, double value, CSSPrimitiveValue::UnitType unit)
{
    style.setProperty(propertyID, CSSValuePool::singleton().createValue(value, unit));
}

void StyledElement::addPropertyToPresentationAttributeStyle(MutableStyleProperties& style, CSSPropertyID propertyID, const String& value)
{
    style.setProperty(propertyID, value, false, CSSParserContext(document()));
}

}
