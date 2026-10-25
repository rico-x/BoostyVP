# BoostyVP

> [!IMPORTANT]
> **[Руководство пользователя — установка, вход и управление с пульта](docs/USER_GUIDE.md)**
>
> **[English user guide](docs/USER_GUIDE.en.md)** · [English README](README.en.md)

[![Проверки](https://github.com/rico-x/BoostyVP/actions/workflows/verify-build.yml/badge.svg)](https://github.com/rico-x/BoostyVP/actions/workflows/verify-build.yml)
[![CodeQL](https://github.com/rico-x/BoostyVP/actions/workflows/codeql.yml/badge.svg)](https://github.com/rico-x/BoostyVP/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/rico-x/BoostyVP/badge)](https://scorecard.dev/viewer/?uri=github.com/rico-x/BoostyVP)

**Ваши подписки Boosty — на большом экране.**

Независимый клиент Boosty для Android TV / Google TV: нативный плеер, русский
и английский интерфейсы, тёмные темы и управление обычным пультом.
SmartTube служит ориентиром удобства; BoostyVP не является его версией для Boosty.

[Сайт проекта](https://rico-x.github.io/BoostyVP/) · [Выпуски](https://github.com/rico-x/BoostyVP/releases) ·
[Документация](docs/README.md) · [Изменения](CHANGELOG.md) · [Roadmap](docs/ROADMAP.md)

![Подписки в текущем интерфейсе BoostyVP](docs/screenshots/ru/home.webp)

| Каталог | Просмотр |
|---|---|
| ![Лента «Новое»](docs/screenshots/ru/new.webp) | ![Нативный плеер](docs/screenshots/ru/player.webp) |
| ![Страница автора](docs/screenshots/ru/author.webp) | ![Комментарии к видеопубликации](docs/screenshots/ru/comments.webp) |
| ![Настройка интерфейса](docs/screenshots/ru/interface.webp) | ![Настройка меню](docs/screenshots/ru/menu-settings.webp) |

## Возможности

- Подписки по авторам, общая лента «Новое», страницы авторов, история
  и продолжение просмотра отдельно для каждого аккаунта.
- «Смотреть позже», избранные видео и авторы, отметки просмотренного,
  поиск по загруженному каталогу и очередь текущего сеанса.
- Media3-плеер: ускоренная перемотка, качество, общая скорость или скорость
  автора, следующее видео, таймер сна, сведения о потоке и восстановление ссылок.
  При уходе в фон видео ставится на паузу; позиция сохраняется с учётом приватности.
- Текстовые публикации с изображениями и комментариями. У видеокарточки
  долгое OK → «Комментарии» открывает обсуждение без запуска ролика.
- Пять размеров карточек, независимые подписи, плотность, оформление выделения,
  компактность страницы автора, тёмная тема и акценты. Меню сдвигает каталог;
  его ширина, плотность, текст, порядок и видимость разделов настраиваются.
- Немедленное сохранение настроек, управление библиотекой и приватностью,
  экспорт/импорт несекретных настроек, каналы Android TV / Projectivy,
  ручная проверка обновлений.

Поиск ограничен загруженными карточками; очередь не переживает завершение
процесса. Комментарии доступны только для чтения. Синхронизация библиотеки
между устройствами, live, SponsorBlock и превью кадров на шкале не заявляются.

## Установка

Android **6.0+** (API 23), пакет `io.github.ricox.boostyvp`. Сборка предусматривает
ARMv7, ARM64, x86, x86_64 и **universal**. Если архитектура Android на ТВ
неизвестна, выбирайте universal: разрядность процессора и ОС могут различаться.
Обновление тем же пакетом и ключом ставится поверх, сохраняя вход и данные.

Публичные APK появляются только в [Releases](https://github.com/rico-x/BoostyVP/releases)
и на [сайте](https://rico-x.github.io/BoostyVP/). Если выпусков нет, готового
публичного APK пока нет. Документация описывает текущий `main`; состав
опубликованного APK определяется его журналом изменений. Debug-артефакты
Actions не являются стабильным выпуском.

**[Пошаговая установка и вход →](docs/USER_GUIDE.md#установка)**

## Документация и статус

- [Руководство пользователя](docs/USER_GUIDE.md) · [English](docs/USER_GUIDE.en.md).
- [Вся документация RU / EN](docs/README.md), [проверки и готовность к выпуску](docs/RELEASE_READINESS.md).
- [Разработка](docs/DEVELOPMENT.md), [публикация и проверка APK](docs/RELEASING.md), [roadmap](docs/ROADMAP.md).
- [Участие](CONTRIBUTING.md), [роль LLM](docs/AI_ASSISTANCE.md), [данные](docs/PRIVACY.md), [безопасность](SECURITY.md).

Ранняя экспериментальная версия. Для платных публикаций нужна действующая
подписка; ограничения доступа не обходятся. BoostyVP не связан с администрацией
Boosty или SmartTube. В самом приложении нет LLM, рекламы и аналитики проекта.

**MIT распространяется только на оригинальные изменения форка.** Унаследованный
код ARDcode не перелицензирован: [лицензия](LICENSE.md), [происхождение](NOTICE.md),
[сторонние компоненты](docs/THIRD_PARTY.md), [отказ от гарантий](DISCLAIMER.md).
